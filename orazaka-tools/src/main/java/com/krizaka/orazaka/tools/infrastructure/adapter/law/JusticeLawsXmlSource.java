package com.krizaka.orazaka.tools.infrastructure.adapter.law;

import com.krizaka.orazaka.tools.application.port.law.LawSource;
import com.krizaka.orazaka.tools.domain.model.law.LawArticle;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.web.client.RestClient;

/**
 * Federal law from Justice Canada's consolidated XML ({@code /eng/XML/<code>.xml} and {@code
 * /fra/XML/<code>.xml}), open data under the Open Government Licence - Canada.
 */
public final class JusticeLawsXmlSource implements LawSource {

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
  public boolean serves(String jurisdiction) {
    return "CA".equals(jurisdiction);
  }

  @Override
  public List<LawArticle> fetch(String jurisdiction, Collection<LawTarget> targets) {
    List<LawArticle> articles = new ArrayList<>();
    for (LawTarget target : targets) {
      articles.addAll(read("eng", "en", target.instrument()));
      articles.addAll(read("fra", "fr", target.instrument()));
    }
    return articles;
  }

  private List<LawArticle> read(String path, String language, String instrument) {
    String url = BASE + path + "/XML/" + instrument + ".xml";
    byte[] xml = client.get().uri(url).retrieve().body(byte[].class);
    if (xml == null || xml.length == 0) {
      throw new IllegalStateException("the federal XML of " + instrument + " answered empty");
    }
    return parser.parseJustice(new ByteArrayInputStream(xml), instrument, language, url);
  }
}
