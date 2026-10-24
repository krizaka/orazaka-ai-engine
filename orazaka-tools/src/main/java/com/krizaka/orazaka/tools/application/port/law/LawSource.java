package com.krizaka.orazaka.tools.application.port.law;

import com.krizaka.orazaka.tools.domain.model.law.LawArticle;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import java.util.Collection;
import java.util.List;

/** An official, open-data source of consolidated statutes (Justice Canada, Données Québec). */
public interface LawSource {

  /**
   * Whether this source publishes the law of the jurisdiction.
   *
   * @param jurisdiction {@code CA} or {@code QC}
   * @return true when {@link #fetch} can serve the targets of that jurisdiction
   */
  boolean serves(String jurisdiction);

  /**
   * Reads the targeted articles from the source, in every language it publishes them. An article
   * that the source no longer holds in force is absent from the result.
   *
   * @param jurisdiction the jurisdiction, for which {@link #serves} is true
   * @param targets the targets of that jurisdiction
   * @return the articles found, all languages, targeted references only
   */
  List<LawArticle> fetch(String jurisdiction, Collection<LawTarget> targets);
}
