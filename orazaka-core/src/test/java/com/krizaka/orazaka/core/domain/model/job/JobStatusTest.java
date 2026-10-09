package com.krizaka.orazaka.core.domain.model.job;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JobStatusTest {

  @Test
  void values_containsAll() {
    assertEquals(4, JobStatus.values().length);
  }

  @Test
  void fromString_validUpperCase() {
    assertEquals(JobStatus.PENDING, JobStatus.fromString("PENDING"));
  }

  @Test
  void fromString_caseInsensitive() {
    assertEquals(JobStatus.COMPLETED, JobStatus.fromString("completed"));
    assertEquals(JobStatus.PROCESSING, JobStatus.fromString("Processing"));
  }

  @Test
  void fromString_null_throws() {
    assertThrows(IllegalArgumentException.class, () -> JobStatus.fromString(null));
  }

  @Test
  void fromString_blank_throws() {
    assertThrows(IllegalArgumentException.class, () -> JobStatus.fromString("  "));
  }

  @Test
  void fromString_invalid_throws() {
    assertThrows(IllegalArgumentException.class, () -> JobStatus.fromString("UNKNOWN"));
  }
}
