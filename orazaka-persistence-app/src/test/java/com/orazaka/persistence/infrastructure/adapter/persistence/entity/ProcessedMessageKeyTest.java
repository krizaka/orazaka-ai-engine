package com.orazaka.persistence.infrastructure.adapter.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessedMessageKeyTest {

  @Test
  @DisplayName("Keys with the same consumer and messageId are equal (composite PK semantics)")
  void equalitySemantics() {
    assertThat(new ProcessedMessageKey("router.jobs", "m-1"))
        .isEqualTo(new ProcessedMessageKey("router.jobs", "m-1"))
        .isNotEqualTo(new ProcessedMessageKey("automation.jobs", "m-1"));
  }

  @Test
  @DisplayName("Null components are rejected")
  void nullComponentsRejected() {
    assertThatNullPointerException().isThrownBy(() -> new ProcessedMessageKey(null, "m-1"));
    assertThatNullPointerException().isThrownBy(() -> new ProcessedMessageKey("router.jobs", null));
  }
}
