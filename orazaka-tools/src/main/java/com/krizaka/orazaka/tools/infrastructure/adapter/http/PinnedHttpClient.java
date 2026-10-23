package com.krizaka.orazaka.tools.infrastructure.adapter.http;

import com.krizaka.orazaka.tools.domain.model.research.FetchLimits;
import com.krizaka.orazaka.tools.domain.model.research.FetchRefusal;
import com.krizaka.orazaka.tools.domain.model.research.PublicAddresses;
import com.krizaka.orazaka.tools.domain.port.HostResolver;
import com.krizaka.orazaka.tools.domain.port.SafeHttp;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The research HTTP client that cannot be pointed at the inside (ADR-087 §4).
 *
 * <p>Per hop: the host is resolved <b>once</b>; every address must be public; then the caller's hop
 * check runs (scheme, the source's patterns, robots.txt); then the connection goes to exactly the
 * addresses that were checked — the client's resolver answers from that list and nothing else, so a
 * DNS answer that changes between the check and the connection (rebinding) cannot reach the
 * connection. TLS still verifies the certificate against the host name. Redirects are not followed
 * by the library: each one is a new hop, checked again. No cookies are sent or kept; connect and
 * read times and the body size are capped.
 */
@Component
public class PinnedHttpClient implements SafeHttp {

  private final HostResolver resolver;
  private final Predicate<InetAddress> addressPolicy;

  /**
   * The production client: public addresses only.
   *
   * @param resolver the host resolver
   */
  @Autowired
  public PinnedHttpClient(HostResolver resolver) {
    this(resolver, PublicAddresses::isPublic);
  }

  /**
   * A client with another address policy — for tests that serve pages on a local port only.
   *
   * @param resolver the host resolver
   * @param addressPolicy which addresses may be connected to
   */
  public PinnedHttpClient(HostResolver resolver, Predicate<InetAddress> addressPolicy) {
    this.resolver = Objects.requireNonNull(resolver, "HostResolver cannot be null");
    this.addressPolicy = Objects.requireNonNull(addressPolicy, "address policy cannot be null");
  }

  @Override
  public Outcome get(URI uri, FetchLimits limits, Function<URI, Optional<FetchRefusal>> hopCheck) {
    URI hop = uri;
    for (int hops = 0; hops <= limits.maxRedirects(); hops++) {
      String scheme = hop.getScheme() == null ? "" : hop.getScheme().toLowerCase(Locale.ROOT);
      if (!("https".equals(scheme) || "http".equals(scheme)) || hop.getHost() == null) {
        return refused(FetchRefusal.SCHEME);
      }
      List<InetAddress> addresses;
      try {
        addresses = resolver.resolve(hop.getHost());
      } catch (UnknownHostException unknown) {
        return refused(FetchRefusal.HTTP_ERROR);
      }
      if (addresses.isEmpty() || !addresses.stream().allMatch(addressPolicy)) {
        return refused(FetchRefusal.SSRF);
      }
      Optional<FetchRefusal> refusal = hopCheck.apply(hop);
      if (refusal.isPresent()) {
        return refused(refusal.get());
      }
      HopAnswer answer;
      try {
        answer = read(hop, addresses, limits);
      } catch (InterruptedIOException slow) {
        return refused(FetchRefusal.TIMEOUT);
      } catch (TooLargeException tooLarge) {
        return refused(FetchRefusal.SIZE);
      } catch (IOException failed) {
        return refused(FetchRefusal.HTTP_ERROR);
      }
      if (answer.status() >= 300 && answer.status() < 400) {
        if (answer.location() == null) {
          return refused(FetchRefusal.REDIRECTS);
        }
        try {
          hop = hop.resolve(answer.location()).normalize();
        } catch (IllegalArgumentException badLocation) {
          return refused(FetchRefusal.REDIRECTS);
        }
        continue;
      }
      return new Outcome(Optional.of(answer.response(hop)), Optional.empty());
    }
    return refused(FetchRefusal.REDIRECTS);
  }

  private static Outcome refused(FetchRefusal reason) {
    return new Outcome(Optional.empty(), Optional.of(reason));
  }

  private record HopAnswer(
      int status,
      String contentType,
      byte[] body,
      String location,
      String etag,
      String lastModified) {
    Response response(URI finalUri) {
      return new Response(finalUri, status, contentType, body, etag, lastModified);
    }
  }

  private static final class TooLargeException extends IOException {
    private static final long serialVersionUID = 1L;

    TooLargeException() {
      super("body larger than the research limit");
    }
  }

  /** Answers only for the checked host, and only with the checked addresses. */
  private record PinnedResolver(String host, List<InetAddress> addresses) implements DnsResolver {
    @Override
    public InetAddress[] resolve(String name) throws UnknownHostException {
      if (!name.equalsIgnoreCase(host)) {
        throw new UnknownHostException(name);
      }
      return addresses.toArray(new InetAddress[0]);
    }

    @Override
    public String resolveCanonicalHostname(String name) {
      return name;
    }
  }

  private HopAnswer read(URI hop, List<InetAddress> checked, FetchLimits limits)
      throws IOException {
    String host = hop.getHost();
    DnsResolver pinnedResolver = new PinnedResolver(host, List.copyOf(checked));
    Timeout timeout = Timeout.of(limits.timeoutMs(), TimeUnit.MILLISECONDS);
    try (CloseableHttpClient client =
        HttpClients.custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(pinnedResolver)
                    .setDefaultConnectionConfig(
                        ConnectionConfig.custom()
                            .setConnectTimeout(timeout)
                            .setSocketTimeout(timeout)
                            .build())
                    .build())
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setResponseTimeout(timeout)
                    .setRedirectsEnabled(false)
                    .build())
            .disableRedirectHandling()
            .disableCookieManagement()
            .disableAuthCaching()
            .setUserAgent(limits.userAgent())
            .build()) {
      HttpGet get = new HttpGet(hop);
      get.setHeader("Accept", String.join(", ", limits.contentTypes()) + ", */*;q=0.1");
      return client.execute(get, response -> answer(response, limits.maxBytes()));
    }
  }

  private static HopAnswer answer(ClassicHttpResponse response, int maxBytes) throws IOException {
    HttpEntity entity = response.getEntity();
    if (entity != null && entity.getContentLength() > maxBytes) {
      throw new TooLargeException();
    }
    byte[] body = entity == null ? new byte[0] : capped(entity.getContent(), maxBytes);
    String type = entity == null || entity.getContentType() == null ? "" : entity.getContentType();
    int semicolon = type.indexOf(';');
    String mediaType =
        (semicolon < 0 ? type : type.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    return new HopAnswer(
        response.getCode(),
        mediaType,
        body,
        header(response, "Location"),
        header(response, "ETag"),
        header(response, "Last-Modified"));
  }

  private static String header(ClassicHttpResponse response, String name) {
    Header header = response.getFirstHeader(name);
    return header == null ? null : header.getValue();
  }

  private static byte[] capped(InputStream in, int maxBytes) throws IOException {
    try (InputStream stream = in) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int total = 0;
      int read;
      while ((read = stream.read(buffer)) != -1) {
        total += read;
        if (total > maxBytes) {
          throw new TooLargeException();
        }
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    }
  }
}
