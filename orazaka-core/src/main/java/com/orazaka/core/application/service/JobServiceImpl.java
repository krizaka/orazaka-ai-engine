package com.orazaka.core.application.service;

import com.orazaka.core.domain.event.JobStatusChangedEvent;
import com.orazaka.core.domain.model.job.JobInfo;
import com.orazaka.core.domain.model.job.JobStatus;
import com.orazaka.core.domain.ports.inbound.JobService;
import com.orazaka.jobs.domain.model.DataClass;
import com.orazaka.persistence.domain.model.JobDto;
import com.orazaka.persistence.domain.ports.inbound.JobPersistenceProvider;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Package-private implementation of the JobService inbound port. Delegates to the out-of-boundary
 * persistence package. Follows ERR-105 (Interface-Driven Boundaries).
 */
@Service
class JobServiceImpl implements JobService {

  private final JobPersistenceProvider jobPersistenceProvider;
  private final ApplicationEventPublisher eventPublisher;

  JobServiceImpl(
      JobPersistenceProvider jobPersistenceProvider, ApplicationEventPublisher eventPublisher) {
    this.jobPersistenceProvider =
        Objects.requireNonNull(jobPersistenceProvider, "JobPersistenceProvider cannot be null");
    this.eventPublisher =
        Objects.requireNonNull(eventPublisher, "ApplicationEventPublisher cannot be null");
  }

  @Override
  public String createJob(
      String userId, String featureKey, Map<String, Object> payload, DataClass dataClass) {
    String jobId = jobPersistenceProvider.createJob(userId, featureKey, payload, dataClass);
    publishEvent(jobId);
    return jobId;
  }

  @Override
  public String createJob(
      String jobId,
      String userId,
      String featureKey,
      Map<String, Object> payload,
      DataClass dataClass) {
    String createdId =
        jobPersistenceProvider.createJob(jobId, userId, featureKey, payload, dataClass);
    publishEvent(createdId);
    return createdId;
  }

  @Override
  public void updateJobStatus(
      String jobId, JobStatus status, Map<String, Object> result, String errorMessage) {
    jobPersistenceProvider.updateJobStatus(jobId, status.name(), result, errorMessage);
    publishEvent(jobId);
  }

  @Override
  public Optional<JobInfo> getJob(String id) {
    return jobPersistenceProvider.getJob(id).map(JobServiceImpl::toInfo);
  }

  @Override
  public Page<JobInfo> getJobsByUserId(String userId, Pageable pageable) {
    return jobPersistenceProvider.getJobsByUserId(userId, pageable).map(JobServiceImpl::toInfo);
  }

  @Override
  public Page<JobInfo> getAllJobs(Pageable pageable) {
    return jobPersistenceProvider.getAllJobs(pageable).map(JobServiceImpl::toInfo);
  }

  @Override
  public void purgeJobsByUserId(String userId) {
    jobPersistenceProvider.purgeJobsByUserId(userId);
  }

  private void publishEvent(String jobId) {
    getJob(jobId)
        .ifPresent(jobInfo -> eventPublisher.publishEvent(new JobStatusChangedEvent(jobInfo)));
  }

  private static JobInfo toInfo(JobDto dto) {
    return new JobInfo(
        dto.id(),
        dto.userId(),
        dto.featureKey(),
        JobStatus.fromString(dto.status()),
        dto.payload(),
        dto.result(),
        dto.errorMessage(),
        dto.createdAt(),
        dto.updatedAt());
  }
}
