package com.krizaka.orazaka.tools.application.service;

import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.orazaka.tools.domain.model.research.*;
import com.krizaka.orazaka.tools.domain.port.PageFetcher;
import com.krizaka.orazaka.tools.domain.port.RateGate;
import com.krizaka.orazaka.tools.domain.port.ResearchStore;
import com.krizaka.orazaka.tools.domain.port.SafeHttp;
import com.krizaka.orazaka.tools.domain.port.TextExtractor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Reads one page for grounded research with every guard of ADR-087 §4, in this order: research on
 * and the source enabled; https (or http where the source allows it); the source's allow and deny
 * patterns; the cache; the source's and the host's rate; robots.txt; then the {@link SafeHttp} GET,
 * whose every hop is checked again (scheme, patterns, robots) and pinned to a public address; the
 * content type; the extraction; the document stored once by URL and content hash. Every outcome —
 * read, served from the cache, refused — writes one {@code research_fetch} line, with the path
 * hashed and the query never kept.
 *
 * <p>robots.txt is cached per origin for at most 24 hours (RFC 9309 §2.4) and cannot be switched
 * off: there is no setting for it.
 */
@Service
public class PageFetchService implements PageFetcher {

  private static final Duration ROBOTS_TTL = Duration.ofHours(24);

  private record CachedRobots(RobotsRules rules, Instant until) {}

  private final RuntimeConfigProvider config;
  private final ResearchStore store;
  private final SafeHttp http;
  private final TextExtractor extractor;
  private final RateGate rates;
  private final Clock clock;
  private final Map<String, CachedRobots> robots = new ConcurrentHashMap<>();

  /**
   * The production fetcher, on the system clock.
   *
   * @param config the runtime settings ({@code research.*})
   * @param store sources, documents and audit
   * @param http the guarded HTTP client
   * @param extractor the text extraction
   * @param rates the per-minute budgets
   */
  @org.springframework.beans.factory.annotation.Autowired
  public PageFetchService(
      RuntimeConfigProvider config,
      ResearchStore store,
      SafeHttp http,
      TextExtractor extractor,
      RateGate rates) {
    this(config, store, http, extractor, rates, Clock.systemUTC());
  }

  /**
   * Wires the fetcher.
   *
   * @param config the runtime settings ({@code research.*})
   * @param store sources, documents and audit
   * @param http the guarded HTTP client
   * @param extractor the text extraction
   * @param rates the per-minute budgets
   * @param clock the clock
   */
  public PageFetchService(
      RuntimeConfigProvider config,
      ResearchStore store,
      SafeHttp http,
      TextExtractor extractor,
      RateGate rates,
      Clock clock) {
    this.config = Objects.requireNonNull(config, "RuntimeConfigProvider cannot be null");
    this.store = Objects.requireNonNull(store, "ResearchStore cannot be null");
    this.http = Objects.requireNonNull(http, "SafeHttp cannot be null");
    this.extractor = Objects.requireNonNull(extractor, "TextExtractor cannot be null");
    this.rates = Objects.requireNonNull(rates, "RateGate cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public FetchResult fetch(FetchRequest request) {
    Instant started = clock.instant();
    URI uri = parse(request.url());
    String host = uri == null || uri.getHost() == null ? null : uri.getHost();
    String pathHash = uri == null ? null : sha256(pathAndQuery(uri));
    Optional<ResearchSource> found = store.source(request.sourceKey());
    UUID sourceId = found.map(ResearchSource::id).orElse(null);

    if (!config.getBoolean("research.enabled", false)
        || found.isEmpty()
        || !found.get().enabled()) {
      return refuse(request, sourceId, host, pathHash, started, FetchRefusal.DISABLED);
    }
    ResearchSource source = found.get();
    FetchLimits limits = limits();
    if (uri == null || host == null) {
      return refuse(request, sourceId, host, pathHash, started, FetchRefusal.SCHEME);
    }
    Optional<FetchRefusal> first = checkScheme(source, uri).or(() -> checkPatterns(source, uri));
    if (first.isPresent()) {
      return refuse(request, sourceId, host, pathHash, started, first.get());
    }

    Optional<FetchedDocument> cached = store.freshDocument(request.url(), started);
    if (cached.isPresent()) {
      audit(
          request,
          sourceId,
          "FETCH",
          host,
          pathHash,
          started,
          cached.get().httpStatus(),
          null,
          true,
          null);
      return FetchResult.of(cached.get(), true);
    }

    if (!rates.tryAcquire("source:" + source.sourceKey(), source.ratePerMinute(), started)
        || !rates.tryAcquire(
            "host:" + host.toLowerCase(Locale.ROOT), limits.perHostPerMinute(), started)) {
      return refuse(request, sourceId, host, pathHash, started, FetchRefusal.RATE);
    }

    SafeHttp.Outcome outcome =
        http.get(
            uri,
            limits,
            hop ->
                checkScheme(source, hop)
                    .or(() -> checkPatterns(source, hop))
                    .or(() -> checkRobots(request, source, hop, limits)));
    if (outcome.refusal().isPresent()) {
      return refuse(request, sourceId, host, pathHash, started, outcome.refusal().get());
    }
    SafeHttp.Response response = outcome.response().orElseThrow();
    if (response.status() < 200 || response.status() >= 300) {
      audit(
          request,
          sourceId,
          "FETCH",
          host,
          pathHash,
          started,
          response.status(),
          response.body().length,
          false,
          FetchRefusal.HTTP_ERROR);
      return FetchResult.refused(FetchRefusal.HTTP_ERROR);
    }
    if (!limits.contentTypes().contains(response.contentType())) {
      audit(
          request,
          sourceId,
          "FETCH",
          host,
          pathHash,
          started,
          response.status(),
          response.body().length,
          false,
          FetchRefusal.CONTENT_TYPE);
      return FetchResult.refused(FetchRefusal.CONTENT_TYPE);
    }

    TextExtractor.Extracted page =
        extractor.extract(response.body(), response.contentType(), response.finalUri());
    int freshness =
        source.freshnessSeconds() > 0
            ? source.freshnessSeconds()
            : limits.defaultFreshnessSeconds();
    FetchedDocument document =
        new FetchedDocument(
            response.finalUri().toString(),
            sha256(page.text()),
            started,
            response.status(),
            response.contentType(),
            response.body().length,
            page.title(),
            page.text(),
            started.plusSeconds(freshness));
    store.saveDocument(document, request.url(), source.id());
    audit(
        request,
        sourceId,
        "FETCH",
        host,
        pathHash,
        started,
        response.status(),
        response.body().length,
        false,
        null);
    return FetchResult.of(document, false);
  }

  private Optional<FetchRefusal> checkScheme(ResearchSource source, URI uri) {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    boolean ok = "https".equals(scheme) || ("http".equals(scheme) && source.allowHttp());
    return ok && uri.getHost() != null && uri.getUserInfo() == null
        ? Optional.empty()
        : Optional.of(FetchRefusal.SCHEME);
  }

  private static Optional<FetchRefusal> checkPatterns(ResearchSource source, URI uri) {
    String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
    return SourcePatterns.allows(source, uri.getHost(), path)
        ? Optional.empty()
        : Optional.of(FetchRefusal.ALLOWLIST);
  }

  private Optional<FetchRefusal> checkRobots(
      FetchRequest request, ResearchSource source, URI uri, FetchLimits limits) {
    String origin = origin(uri);
    Instant now = clock.instant();
    CachedRobots known = robots.get(origin);
    if (known == null || !now.isBefore(known.until())) {
      known = new CachedRobots(readRobots(request, source, uri, limits), now.plus(ROBOTS_TTL));
      robots.put(origin, known);
    }
    return known.rules().allows(pathAndQuery(uri))
        ? Optional.empty()
        : Optional.of(FetchRefusal.ROBOTS);
  }

  private RobotsRules readRobots(
      FetchRequest request, ResearchSource source, URI page, FetchLimits limits) {
    Instant started = clock.instant();
    URI robotsUri = URI.create(origin(page) + "/robots.txt");
    // Same origin only: a robots.txt that redirects elsewhere is not this site's answer.
    SafeHttp.Outcome outcome =
        http.get(
            robotsUri,
            limits,
            hop ->
                origin(hop).equals(origin(page))
                    ? Optional.empty()
                    : Optional.of(FetchRefusal.REDIRECTS));
    Integer status = outcome.response().map(SafeHttp.Response::status).orElse(null);
    audit(
        request,
        source.id(),
        "ROBOTS",
        page.getHost(),
        sha256("/robots.txt"),
        started,
        status,
        outcome.response().map(r -> r.body().length).orElse(null),
        false,
        outcome.refusal().orElse(null));
    if (outcome.response().isEmpty()) {
      // An SSRF refusal is not "unreachable": the page itself will be refused for the same reason.
      return outcome.refusal().filter(FetchRefusal.SSRF::equals).isPresent()
          ? RobotsRules.allowAll()
          : RobotsRules.disallowAll();
    }
    int s = outcome.response().get().status();
    if (s >= 200 && s < 300) {
      return RobotsRules.parse(
          new String(outcome.response().get().body(), StandardCharsets.UTF_8),
          limits.productToken());
    }
    return s >= 400 && s < 500 ? RobotsRules.allowAll() : RobotsRules.disallowAll();
  }

  private FetchLimits limits() {
    return new FetchLimits(
        config.getInt("research.fetch.timeout-ms", 10_000),
        config.getInt("research.fetch.max-bytes", 2_097_152),
        config.getInt("research.fetch.max-redirects", 3),
        config.getInt("research.fetch.per-host-per-minute", 30),
        Arrays.stream(
                config
                    .getString(
                        "research.fetch.content-types",
                        "text/html,application/xhtml+xml,text/plain,application/xml")
                    .split(","))
            .map(String::trim)
            .filter(t -> !t.isEmpty())
            .toList(),
        config.getString("research.user-agent", "OrazakaResearch/0.1 (+https://www.orazaka.com)"),
        config.getInt("research.cache.default-freshness-seconds", 86_400));
  }

  private FetchResult refuse(
      FetchRequest request,
      UUID sourceId,
      String host,
      String pathHash,
      Instant started,
      FetchRefusal reason) {
    audit(request, sourceId, "FETCH", host, pathHash, started, null, null, false, reason);
    return FetchResult.refused(reason);
  }

  private void audit(
      FetchRequest request,
      UUID sourceId,
      String operation,
      String host,
      String pathHash,
      Instant started,
      Integer status,
      Integer bytes,
      boolean cacheHit,
      FetchRefusal refusal) {
    int duration = (int) Duration.between(started, clock.instant()).toMillis();
    store.audit(
        new FetchAudit(
            started,
            request.actorId(),
            request.runId(),
            request.chatId(),
            sourceId,
            operation,
            host,
            pathHash,
            status,
            bytes,
            duration,
            cacheHit,
            refusal));
  }

  private static URI parse(String url) {
    try {
      URI uri = new URI(url.trim());
      return uri.isAbsolute() ? uri.normalize() : null;
    } catch (java.net.URISyntaxException bad) {
      return null;
    }
  }

  private static String origin(URI uri) {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    return scheme + "://" + host + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
  }

  private static String pathAndQuery(URI uri) {
    String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
    return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
  }

  static String sha256(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is always available", impossible);
    }
  }
}
