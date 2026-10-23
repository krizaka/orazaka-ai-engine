package com.krizaka.orazaka.tools.domain.port;

import com.krizaka.orazaka.tools.domain.model.research.FetchAudit;
import com.krizaka.orazaka.tools.domain.model.research.FetchedDocument;
import com.krizaka.orazaka.tools.domain.model.research.ResearchSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Where grounded research keeps its sources, what it read, and its audit (ADR-087). */
public interface ResearchStore {

  /**
   * A source by its key.
   *
   * @param sourceKey the key
   * @return the source, if declared
   */
  Optional<ResearchSource> source(String sourceKey);

  /**
   * The newest document of a URL still fresh at {@code now}.
   *
   * @param url the URL as requested
   * @param now the instant of the check
   * @return the cached document, if any
   */
  Optional<FetchedDocument> freshDocument(String url, Instant now);

  /**
   * Stores a document once by URL and content hash.
   *
   * @param document what was read
   * @param requestedUrl the URL as requested (also cached, when a redirect changed it)
   * @param sourceId the source it was read through
   */
  void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId);

  /**
   * Writes one audit line.
   *
   * @param audit the line
   */
  void audit(FetchAudit audit);
}
