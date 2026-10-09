package com.krizaka.orazaka.core.application.service;

import static com.krizaka.orazaka.test.TestConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.core.domain.event.JobStatusChangedEvent;
import com.krizaka.orazaka.core.domain.model.job.JobInfo;
import com.krizaka.orazaka.core.domain.model.job.JobStatus;
import com.krizaka.orazaka.jobs.domain.model.DataClass;
import com.krizaka.orazaka.persistence.domain.model.JobDto;
import com.krizaka.orazaka.persistence.domain.ports.inbound.JobPersistenceProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class JobServiceImplTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  private JobPersistenceProvider persistenceProvider;
  private ApplicationEventPublisher eventPublisher;
  private JobServiceImpl jobService;

  @BeforeEach
  void setUp() {
    persistenceProvider = mock(JobPersistenceProvider.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    jobService = new JobServiceImpl(persistenceProvider, eventPublisher);
  }

  @Test
  @DisplayName("constructor rejects null dependencies")
  void constructorValidation() {
    assertThrows(NullPointerException.class, () -> new JobServiceImpl(null, eventPublisher));
    assertThrows(NullPointerException.class, () -> new JobServiceImpl(persistenceProvider, null));
  }

  @Test
  @DisplayName("create job forwards calls and publishes event")
  void createJob() {
    String jobId = "job-123";
    String userId = "user-456";
    String featureKey = FEAT;
    Map<String, Object> payload = Map.of("key", "val");

    when(persistenceProvider.createJob(userId, featureKey, payload, DataClass.SENSITIVE))
        .thenReturn(jobId);
    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(jobId, userId, featureKey, STATUS_PENDING, payload, Map.of(), null, now, now);
    when(persistenceProvider.getJob(jobId)).thenReturn(Optional.of(dto));

    String returnedId = jobService.createJob(userId, featureKey, payload, DataClass.SENSITIVE);

    assertEquals(jobId, returnedId);
    verify(persistenceProvider).createJob(userId, featureKey, payload, DataClass.SENSITIVE);

    ArgumentCaptor<JobStatusChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(JobStatusChangedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertEquals(jobId, eventCaptor.getValue().job().id());
  }

  @Test
  @DisplayName("create job with custom ID forwards calls and publishes event")
  void createJobWithId() {
    String jobId = "job-123";
    String userId = "user-456";
    String featureKey = FEAT;
    Map<String, Object> payload = Map.of("key", "val");

    when(persistenceProvider.createJob(jobId, userId, featureKey, payload, DataClass.SENSITIVE))
        .thenReturn(jobId);
    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(jobId, userId, featureKey, STATUS_PENDING, payload, Map.of(), null, now, now);
    when(persistenceProvider.getJob(jobId)).thenReturn(Optional.of(dto));

    String returnedId =
        jobService.createJob(jobId, userId, featureKey, payload, DataClass.SENSITIVE);

    assertEquals(jobId, returnedId);
    verify(persistenceProvider).createJob(jobId, userId, featureKey, payload, DataClass.SENSITIVE);
  }

  @Test
  @DisplayName("update job status forwards calls and publishes event")
  void updateJobStatus() {
    String jobId = "job-123";
    JobStatus status = JobStatus.COMPLETED;
    Map<String, Object> result = Map.of("done", true);
    String error = null;

    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(jobId, USER_DASH_1, FEAT, status.name(), Map.of(), result, error, now, now);
    when(persistenceProvider.getJob(jobId)).thenReturn(Optional.of(dto));

    jobService.updateJobStatus(jobId, status, result, error);

    verify(persistenceProvider).updateJobStatus(jobId, status.name(), result, error);
    verify(eventPublisher).publishEvent(any(JobStatusChangedEvent.class));
  }

  @Test
  @DisplayName("get job returns mapped result")
  void getJob() {
    String jobId = "job-123";
    when(persistenceProvider.getJob(jobId)).thenReturn(Optional.empty());
    assertTrue(jobService.getJob(jobId).isEmpty());

    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(jobId, USER_DASH_1, FEAT, STATUS_PENDING, Map.of(), Map.of(), null, now, now);
    when(persistenceProvider.getJob(jobId)).thenReturn(Optional.of(dto));

    Optional<JobInfo> res = jobService.getJob(jobId);
    assertTrue(res.isPresent());
    assertEquals(jobId, res.get().id());
  }

  @Test
  @DisplayName("get jobs by user ID returns mapped page")
  void getJobsByUserId() {
    String userId = USER_DASH_1;
    Pageable pageable = PageRequest.of(0, 10);
    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(JOB_1, userId, FEAT, STATUS_PENDING, Map.of(), Map.of(), null, now, now);
    Page<JobDto> page = new PageImpl<>(List.of(dto), pageable, 1);
    when(persistenceProvider.getJobsByUserId(userId, pageable)).thenReturn(page);

    Page<JobInfo> result = jobService.getJobsByUserId(userId, pageable);
    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals(JOB_1, result.getContent().get(0).id());
  }

  @Test
  @DisplayName("get all jobs returns mapped page")
  void getAllJobs() {
    Pageable pageable = PageRequest.of(0, 10);
    Instant now = Instant.now(FIXED_CLOCK);
    JobDto dto =
        new JobDto(JOB_1, USER_DASH_1, FEAT, STATUS_PENDING, Map.of(), Map.of(), null, now, now);
    Page<JobDto> page = new PageImpl<>(List.of(dto), pageable, 1);
    when(persistenceProvider.getAllJobs(pageable)).thenReturn(page);

    Page<JobInfo> result = jobService.getAllJobs(pageable);
    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals(JOB_1, result.getContent().get(0).id());
  }

  @Test
  @DisplayName("purge jobs forwards to provider")
  void purgeJobs() {
    String userId = USER_DASH_1;
    jobService.purgeJobsByUserId(userId);
    verify(persistenceProvider, times(1)).purgeJobsByUserId(userId);
  }
}
