package com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** The key of a research document: the URL and the hash of what was read there. */
@Embeddable
public class ResearchDocumentId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "url")
  private String url;

  @Column(name = "content_hash")
  private String contentHash;

  public ResearchDocumentId() {
    /* JPA requires a no-arg constructor */
  }

  public ResearchDocumentId(String url, String contentHash) {
    this.url = url;
    this.contentHash = contentHash;
  }

  public String getUrl() {
    return url;
  }

  public String getContentHash() {
    return contentHash;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ResearchDocumentId that
        && Objects.equals(url, that.url)
        && Objects.equals(contentHash, that.contentHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, contentHash);
  }
}
