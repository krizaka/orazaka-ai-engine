package com.krizaka.orazaka.core.infrastructure.checks;

import com.krizaka.orazaka.core.domain.model.checks.CheckPurpose;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckHttp;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Certn: the fallback for subjects outside Québec, court records and identity
 * (orazaka-ai-engine#73). Its old API is deprecated; CertnCentric's authentication and webhook
 * signing are confirmed in writing when the account is opened.
 */
public final class CertnConnector extends HttpCheckConnector {

  /** The provider and handler name. */
  private static final String PROVIDER = "certn";

  /**
   * @param http the HTTP door
   * @param timeout how long a search may take
   */
  public CertnConnector(CheckHttp http, Duration timeout) {
    super(http, timeout);
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  Set<CheckPurpose> supports() {
    return Set.of(CheckPurpose.COURT_RECORDS, CheckPurpose.IDENTITY);
  }

  @Override
  Map<String, String> authHeaders(String key) {
    return Map.of("Authorization", "Api-Key " + key, "Content-Type", "application/json");
  }

  @Override
  String path(CheckPurpose purpose) {
    return purpose == CheckPurpose.COURT_RECORDS ? "/court-records" : "/identity";
  }
}
