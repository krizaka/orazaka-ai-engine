package com.krizaka.orazaka.tools.infrastructure.adapter.law;

import com.krizaka.orazaka.tools.application.port.law.LawSource;
import com.krizaka.orazaka.tools.domain.model.law.LawArticle;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.web.client.RestClient;

/**
 * Québec law from the Données Québec open-data snapshot of the consolidated statutes and
 * regulations (XML, one zip per collection). Only the targeted instruments are read from it.
 */
public final class QuebecOpenDataSource implements LawSource {

  private static final Pattern STATUTE_ENTRY =
      Pattern.compile("Statutes/([^/]+)/(FR|EN)/\\1_\\2\\.xml");

  private final RestClient client;
  private final String datasetUrl;
  private final LawStatuteXmlParser parser;

  /**
   * Creates the source.
   *
   * @param client the HTTP client, with an honest user agent
   * @param datasetUrl the URL of the statutes zip of the snapshot
   * @param parser the XML parser
   */
  public QuebecOpenDataSource(RestClient client, String datasetUrl, LawStatuteXmlParser parser) {
    this.client = client;
    this.datasetUrl = datasetUrl;
    this.parser = parser;
  }

  @Override
  public boolean serves(String jurisdiction) {
    return "QC".equals(jurisdiction);
  }

  @Override
  public List<LawArticle> fetch(String jurisdiction, Collection<LawTarget> targets) {
    Set<String> wanted = targets.stream().map(LawTarget::instrument).collect(Collectors.toSet());
    byte[] zip = client.get().uri(datasetUrl).retrieve().body(byte[].class);
    if (zip == null || zip.length == 0) {
      throw new IllegalStateException("the Données Québec statutes zip answered empty");
    }
    List<LawArticle> articles = new ArrayList<>();
    try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = entries.getNextEntry()) != null) {
        Matcher match = STATUTE_ENTRY.matcher(entry.getName().replace('\\', '/'));
        if (!match.matches() || !wanted.contains(match.group(1))) {
          continue;
        }
        String language = match.group(2).toLowerCase(Locale.ROOT);
        articles.addAll(
            parser.parseQuebec(new UnclosedStream(entries), match.group(1), language, datasetUrl));
      }
    } catch (IOException e) {
      throw new IllegalStateException("the Données Québec zip could not be read", e);
    }
    return articles;
  }

  /** Lets the parser read one zip entry without closing the zip. */
  private static final class UnclosedStream extends FilterInputStream {
    UnclosedStream(InputStream in) {
      super(in);
    }

    @Override
    public void close() {
      // the enclosing zip stream is closed by its owner
    }
  }
}
