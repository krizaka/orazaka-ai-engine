package com.krizaka.orazaka.tools.application.service;

import com.krizaka.orazaka.tools.application.port.law.LawSettings;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import java.util.List;

/** The law settings as a test sets them. */
final class FixedLawSettings implements LawSettings {

  boolean enabled = true;
  int maxArticleChars = 20_000;
  List<LawTarget> instruments =
      List.of(LawTarget.wholeInstrument("QC", "CCQ-1991"), LawTarget.wholeInstrument("CA", "E-15"));

  @Override
  public boolean corpusEnabled() {
    return enabled;
  }

  @Override
  public List<LawTarget> importInstruments() {
    return instruments;
  }

  @Override
  public int maxArticleChars() {
    return maxArticleChars;
  }
}
