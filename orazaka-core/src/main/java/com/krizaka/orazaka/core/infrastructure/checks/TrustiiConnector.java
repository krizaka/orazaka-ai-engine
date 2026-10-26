package com.krizaka.orazaka.core.infrastructure.checks;

import com.krizaka.orazaka.core.domain.model.checks.CheckPurpose;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckHttp;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Trustii: credit (a bureau behind it, Equifax or TransUnion) and identity (orazaka-ai-engine#73).
 * Its API, its bureau and its consent wording are confirmed when the account is opened.
 */
public final class TrustiiConnector extends HttpCheckConnector {

  /** The provider and handler name. */
  private static final String PROVIDER = "trustii";

  /**
   * @param http the HTTP door
   * @param timeout how long a search may take
   */
  public TrustiiConnector(CheckHttp http, Duration timeout) {
    super(http, timeout);
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  Set<CheckPurpose> supports() {
    return Set.of(CheckPurpose.CREDIT, CheckPurpose.IDENTITY);
  }

  @Override
  Map<String, String> authHeaders(String key) {
    return Map.of("Authorization", "Bearer " + key, "Content-Type", "application/json");
  }

  @Override
  String path(CheckPurpose purpose) {
    return purpose == CheckPurpose.CREDIT ? "/credit" : "/identity";
  }
}
