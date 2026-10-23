package com.krizaka.orazaka.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.orazaka.tools.domain.model.research.FetchAudit;
import com.krizaka.orazaka.tools.domain.model.research.FetchRefusal;
import com.krizaka.orazaka.tools.domain.model.research.FetchRequest;
import com.krizaka.orazaka.tools.domain.model.research.FetchResult;
import com.krizaka.orazaka.tools.domain.model.research.FetchedDocument;
import com.krizaka.orazaka.tools.domain.model.research.ResearchSource;
import com.krizaka.orazaka.tools.domain.port.ResearchStore;
import com.krizaka.orazaka.tools.domain.port.TextExtractor;
import com.krizaka.orazaka.tools.infrastructure.adapter.http.PinnedHttpClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every guard of a research fetch, in order, each one leaving its audit line (ADR-087 §4). */
class PageFetchServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

  private HttpServer server;
  private int port;
  private final AtomicInteger pageHits = new AtomicInteger();
  private final String robots = "User-agent: *\nDisallow: /blocked\n";
  private int robotsStatus = 200;

  private final Map<String, String> settings = new HashMap<>();
  private final InMemoryStore store = new InMemoryStore();

  /** Sources, documents and the audit, in memory. */
  private static final class InMemoryStore implements ResearchStore {
    final Map<String, ResearchSource> sources = new HashMap<>();
    final Map<String, FetchedDocument> documents = new HashMap<>();
    final List<FetchAudit> audit = new ArrayList<>();

    @Override
    public Optional<ResearchSource> source(String sourceKey) {
      return Optional.ofNullable(sources.get(sourceKey));
    }

    @Override
    public Optional<FetchedDocument> freshDocument(String url, Instant now) {
      return Optional.ofNullable(documents.get(url)).filter(d -> d.expiresAt().isAfter(now));
    }

    @Override
    public void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId) {
      documents.put(document.url(), document);
      documents.put(requestedUrl, document);
    }

    @Override
    public void audit(FetchAudit audit) {
      this.audit.add(audit);
    }

    List<FetchAudit> fetches() {
      return audit.stream().filter(a -> "FETCH".equals(a.operation())).toList();
    }
  }

  private final class Settings implements RuntimeConfigProvider {
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return settings.containsKey(key) ? Boolean.parseBoolean(settings.get(key)) : defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
      return settings.containsKey(key) ? Integer.parseInt(settings.get(key)) : defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
      return settings.getOrDefault(key, defaultValue);
    }
  }

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/robots.txt", e -> respond(e.getResponseHeaders(), e, robotsStatus, "text/plain", robots));
    server.createContext(
        "/doc",
        e -> {
          pageHits.incrementAndGet();
          respond(e.getResponseHeaders(), e, 200, "text/html", "<p>Article 1904.</p>");
        });
    server.createContext(
        "/blocked",
        e -> {
          pageHits.incrementAndGet();
          respond(e.getResponseHeaders(), e, 200, "text/html", "<p>no</p>");
        });
    server.createContext(
        "/json", e -> respond(e.getResponseHeaders(), e, 200, "application/json", "{}"));
    server.createContext(
        "/gone", e -> respond(e.getResponseHeaders(), e, 404, "text/html", "missing"));
    server.start();
    port = server.getAddress().getPort();
    settings.put("research.enabled", "true");
    settings.put("research.fetch.timeout-ms", "2000");
    store.sources.put("site", source(true, List.of("site.test/**"), true, 10));
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private static void respond(
      com.sun.net.httpserver.Headers headers,
      com.sun.net.httpserver.HttpExchange e,
      int status,
      String type,
      String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    headers.add("Content-Type", type);
    e.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = e.getResponseBody()) {
      out.write(bytes);
    }
    e.close();
  }

  private static ResearchSource source(
      boolean enabled, List<String> allow, boolean allowHttp, int rate) {
    return new ResearchSource(
        UUID.randomUUID(),
        "site",
        "FETCH",
        "http",
        allow,
        List.of(),
        allowHttp,
        3600,
        rate,
        enabled);
  }

  private PageFetchService service() {
    TextExtractor extractor =
        (body, type, base) ->
            new TextExtractor.Extracted("t", new String(body, StandardCharsets.UTF_8));
    return new PageFetchService(
        new Settings(),
        store,
        new PinnedHttpClient(
            host -> List.of(InetAddress.getLoopbackAddress()), InetAddress::isLoopbackAddress),
        extractor,
        new com.krizaka.orazaka.tools.infrastructure.research.TestRateGate(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private String url(String path) {
    return "http://site.test:" + port + path;
  }

  private FetchResult fetch(String url) {
    return service().fetch(new FetchRequest("site", url, "actor-1", null, null));
  }

  @Test
  @DisplayName("research off: nothing is read, and the refusal is audited")
  void offByDefault() {
    settings.remove("research.enabled");
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.DISABLED);
    assertThat(pageHits).hasValue(0);
    assertThat(store.fetches())
        .singleElement()
        .extracting(FetchAudit::refusal)
        .isEqualTo(FetchRefusal.DISABLED);
  }

  @Test
  @DisplayName("a disabled or unknown source reads nothing")
  void disabledSource() {
    store.sources.put("site", source(false, List.of("site.test/**"), true, 10));
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.DISABLED);
    assertThat(service().fetch(new FetchRequest("nope", url("/doc"), null, null, null)).refusal())
        .contains(FetchRefusal.DISABLED);
  }

  @Test
  @DisplayName("http only where the source allows it")
  void httpOnlyWhenAllowed() {
    store.sources.put("site", source(true, List.of("site.test/**"), false, 10));
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.SCHEME);
  }

  @Test
  @DisplayName("outside the allow list, nothing is read")
  void allowList() {
    store.sources.put("site", source(true, List.of("site.test/other/**"), true, 10));
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.ALLOWLIST);
    assertThat(pageHits).hasValue(0);
  }

  @Test
  @DisplayName("a page is read, stored by URL and hash; the audit has a path hash, never the query")
  void readsStoresAndAudits() {
    FetchResult result = fetch(url("/doc?q=secret-query"));
    assertThat(result.document()).isPresent();
    FetchedDocument doc = result.document().get();
    assertThat(doc.text()).isEqualTo("<p>Article 1904.</p>");
    assertThat(doc.contentHash()).hasSize(64);
    assertThat(doc.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    FetchAudit line = store.fetches().getLast();
    assertThat(line.refusal()).isNull();
    assertThat(line.httpStatus()).isEqualTo(200);
    assertThat(line.pathHash()).hasSize(64).doesNotContain("secret");
    assertThat(line.host()).isEqualTo("site.test");
    assertThat(line.toString()).doesNotContain("secret-query");
  }

  @Test
  @DisplayName("within freshness, a second read makes no HTTP call")
  void cacheHit() {
    fetch(url("/doc"));
    FetchResult again = fetch(url("/doc"));
    assertThat(again.cacheHit()).isTrue();
    assertThat(pageHits).hasValue(1);
    assertThat(store.fetches().getLast().cacheHit()).isTrue();
  }

  @Test
  @DisplayName("robots.txt is honoured; a 5xx robots.txt allows nothing; a 404 allows everything")
  void robots() {
    assertThat(fetch(url("/blocked")).refusal()).contains(FetchRefusal.ROBOTS);
    assertThat(pageHits).hasValue(0);
    assertThat(store.audit).anyMatch(a -> "ROBOTS".equals(a.operation()));

    robotsStatus = 503;
    assertThat(service().fetch(new FetchRequest("site", url("/doc"), null, null, null)).refusal())
        .contains(FetchRefusal.ROBOTS);

    robotsStatus = 404;
    assertThat(service().fetch(new FetchRequest("site", url("/doc"), null, null, null)).document())
        .isPresent();
  }

  @Test
  @DisplayName("the source's rate is a hard stop")
  void rate() {
    store.sources.put("site", source(true, List.of("site.test/**"), true, 1));
    PageFetchService one = service();
    assertThat(one.fetch(new FetchRequest("site", url("/doc"), null, null, null)).document())
        .isPresent();
    assertThat(one.fetch(new FetchRequest("site", url("/gone"), null, null, null)).refusal())
        .contains(FetchRefusal.RATE);
  }

  @Test
  @DisplayName("an error status or an unread content type is refused and not stored")
  void statusAndType() {
    assertThat(fetch(url("/gone")).refusal()).contains(FetchRefusal.HTTP_ERROR);
    assertThat(fetch(url("/json")).refusal()).contains(FetchRefusal.CONTENT_TYPE);
    assertThat(store.documents).isEmpty();
  }
}
