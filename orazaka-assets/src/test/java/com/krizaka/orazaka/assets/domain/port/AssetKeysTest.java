package com.krizaka.orazaka.assets.domain.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AssetKeysTest {

  @Test
  void followsTheTenantUserJobLayout() {
    assertThat(AssetKeys.jobFile("default", "2561abd5", "a3c88e54", "image.png"))
        .isEqualTo("t/default/u/2561abd5/jobs/a3c88e54/image.png");
    assertThat(AssetKeys.jobPrefix("default", "u1", "j1")).isEqualTo("t/default/u/u1/jobs/j1/");
  }

  @Test
  void refusesAnySegmentThatCouldClimbOrNest() {
    for (String bad : new String[] {"..", "a/b", "", " ", "a\\b", "x..y", null, "/abs"}) {
      assertThatThrownBy(() -> AssetKeys.jobFile("t", "u", "j", bad))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> AssetKeys.jobFile("t", bad, "j", "f"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
