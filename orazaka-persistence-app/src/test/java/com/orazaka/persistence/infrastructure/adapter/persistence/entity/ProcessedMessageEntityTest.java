package com.orazaka.persistence.infrastructure.adapter.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessedMessageEntityTest {

  @Test
  @DisplayName("Always reports isNew so save() INSERTs and duplicates collapse on the PK")
  void alwaysNewForInsertFirstSemantics() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    var entity = new ProcessedMessageEntity(new ProcessedMessageKey("router.jobs", "m-1"), now);

    assertThat(entity.isNew()).isTrue();
    assertThat(entity.getId()).isEqualTo(new ProcessedMessageKey("router.jobs", "m-1"));
    assertThat(entity.getProcessedAt()).isEqualTo(now);
  }
}
