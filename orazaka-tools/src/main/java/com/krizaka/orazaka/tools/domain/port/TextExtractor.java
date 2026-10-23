package com.krizaka.orazaka.tools.domain.port;

import java.net.URI;

/** Turns a fetched body into the text a citation can quote: the main content, no markup. */
public interface TextExtractor {

  /**
   * The extracted page.
   *
   * @param title the page title, or empty
   * @param text the main text, paragraphs separated by blank lines
   */
  record Extracted(String title, String text) {}

  /**
   * Extracts the text.
   *
   * @param body the body
   * @param contentType its media type
   * @param base the page's URI
   * @return the title and text
   */
  Extracted extract(byte[] body, String contentType, URI base);
}
