package com.krizaka.orazaka.tools.infrastructure.adapter.law;

import com.krizaka.orazaka.tools.application.port.law.LawSource;
import com.krizaka.orazaka.tools.domain.model.law.LawArticle;
import com.krizaka.orazaka.tools.domain.model.law.LawSnapshot;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import com.krizaka.orazaka.tools.domain.model.law.Sha256;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.client.RestClient;

/**
 * Federal law from Justice Canada's consolidated XML ({@code /eng/XML/<code>.xml} and {@code
 * /fra/XML/<code>.xml}), open data under the Open Government Licence - Canada. One file per act and
 * language, so there is no dataset validator: every run reads them.
 */
public final class JusticeLawsXmlSource implements LawSource {

  /** The {@code research_source} the Justice Canada texts are recorded under. */
  static final String SOURCE_KEY = "justice-laws";

  private static final String BASE = "https://laws-lois.justice.gc.ca/";

  private final RestClient client;
  private final LawStatuteXmlParser parser;

  /**
   * Creates the source.
   *
   * @param client the HTTP client, with an honest user agent
   * @param parser the XML parser
   */
  public JusticeLawsXmlSource(RestClient client, LawStatuteXmlParser parser) {
    this.client = client;
    this.parser = parser;
  }

  @Override
  public String sourceKey() {
    return SOURCE_KEY;
  }

  @Override
  public boolean serves(String jurisdiction) {
    return "CA".equals(jurisdiction);
  }

  @Override
  public String datasetUrl(String jurisdiction) {
    return BASE;
  }

  @Override
  public LawSnapshot fetch(
      String jurisdiction, Collection<LawTarget> targets, Optional<String> knownEtag) {
    List<LawArticle> articles = new ArrayList<>();
    StringBuilder digests = new StringBuilder();
    LocalDate consolidatedTo = null;
    for (LawTarget target : targets) {
      for (String[] language : new String[][] {{"eng", "en"}, {"fra", "fr"}}) {
        String url = BASE + language[0] + "/XML/" + target.instrument() + ".xml";
        byte[] xml = client.get().uri(url).retrieve().body(byte[].class);
        if (xml == null || xml.length == 0) {
          throw new IllegalStateException(
              "the federal XML of " + target.instrument() + " answered empty");
        }
        articles.addAll(
            parser.parseJustice(
                new ByteArrayInputStream(xml), target.instrument(), language[1], url));
        digests.append(url).append(' ').append(Sha256.hex(xml)).append('\n');
        LocalDate current = parser.federalConsolidatedTo(xml).orElse(null);
        if (current != null && (consolidatedTo == null || current.isAfter(consolidatedTo))) {
          consolidatedTo = current;
        }
      }
    }
    return new LawSnapshot(
        articles,
        Set.of("fr", "en"),
        BASE,
        null,
        Sha256.hex(digests.toString().getBytes(StandardCharsets.UTF_8)),
        consolidatedTo,
        false);
  }
}
