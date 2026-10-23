package com.krizaka.orazaka.tools.infrastructure.adapter.extraction;

import com.krizaka.orazaka.tools.domain.port.TextExtractor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Readability-style extraction (ADR-087 §2): the page's main content as text, in reading order, one
 * paragraph per block, without what a reader does not read — scripts, styles, navigation, headers
 * and footers, forms, and anything hidden from the reader ({@code hidden}, {@code aria-hidden},
 * {@code display:none}). Text a page hides from people is how it would speak to the model behind
 * their back, so it never reaches a quote.
 *
 * <p>The main content is {@code <main>}, else {@code <article>}, else {@code [role=main]}, else the
 * body. Plain text and XML are kept as they are, decoded as UTF-8.
 */
@Component
class HtmlTextExtractor implements TextExtractor {

  private static final String DROPPED =
      "script, style, noscript, template, iframe, svg, canvas, nav, header, footer, aside, form,"
          + " [hidden], [aria-hidden=true], [style*=display:none], [style*=display: none],"
          + " [style*=visibility:hidden], [style*=visibility: hidden]";

  private static final String BLOCKS =
      "h1, h2, h3, h4, h5, h6, p, li, blockquote, pre, td, th, dt, dd, figcaption";

  @Override
  public Extracted extract(byte[] body, String contentType, URI base) {
    String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    if (!type.contains("html")) {
      return new Extracted("", normalise(new String(body, StandardCharsets.UTF_8)));
    }
    Document page = Jsoup.parse(new String(body, StandardCharsets.UTF_8), base.toString());
    String title = page.title();
    page.select(DROPPED).remove();
    Element main =
        first(
            page.selectFirst("main"),
            page.selectFirst("article"),
            page.selectFirst("[role=main]"),
            page.body());
    if (main == null) {
      return new Extracted(title, "");
    }
    List<String> paragraphs = new ArrayList<>();
    for (Element block : main.select(BLOCKS)) {
      // A block inside another block (a <p> in a <li>) is read with its parent.
      if (insideAnotherBlock(block, main)) {
        continue;
      }
      String text = normalise(block.text());
      if (!text.isEmpty()) {
        paragraphs.add(text);
      }
    }
    String text = paragraphs.isEmpty() ? normalise(main.text()) : String.join("\n\n", paragraphs);
    return new Extracted(title, text);
  }

  private static boolean insideAnotherBlock(Element block, Element main) {
    for (Element parent = block.parent();
        parent != null && parent != main;
        parent = parent.parent()) {
      if (parent.is(BLOCKS)) {
        return true;
      }
    }
    return false;
  }

  private static Element first(Element... candidates) {
    for (Element candidate : candidates) {
      if (candidate != null) {
        return candidate;
      }
    }
    return null;
  }

  private static String normalise(String text) {
    return text.replace(' ', ' ')
        .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
        .replaceAll(" *\\n *", "\n")
        .strip();
  }
}
