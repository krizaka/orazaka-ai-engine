package com.krizaka.orazaka.core.infrastructure.checks;

import com.krizaka.orazaka.core.domain.model.checks.CheckPurpose;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckHttp;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * SOQUIJ, Service Web Plumitifs: court records of Québec, the TAL and the civil and penal plumitifs
 * (orazaka-ai-engine#73). Its web service is not public: the endpoint, the key and the wire mapping
 * are confirmed when the account is opened.
 */
public final class SoquijConnector extends HttpCheckConnector {

  /** The provider and handler name. */
  private static final String PROVIDER = "soquij";

  /**
   * @param http the HTTP door
   * @param timeout how long a search may take
   */
  public SoquijConnector(CheckHttp http, Duration timeout) {
    super(http, timeout);
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  Set<CheckPurpose> supports() {
    return Set.of(CheckPurpose.COURT_RECORDS);
  }

  @Override
  Map<String, String> authHeaders(String key) {
    return Map.of("Authorization", "Bearer " + key, "Content-Type", "application/json");
  }

  @Override
  String path(CheckPurpose purpose) {
    return "/plumitifs";
  }
}
