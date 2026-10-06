package com.orazaka.core.infrastructure.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CQRS runtime pool configuration — separates synchronous (Chat/RAG) and asynchronous (Media/Batch)
 * execution runtimes to respect SLA boundaries.
 *
 * <p>SYNCHRONOUS RUNTIME: Virtual Thread per-task executor (Project Loom) for real-time SSE
 * delivery. Zero broker usage — intra-JVM communication uses direct Java method calls.
 *
 * <p>ASYNCHRONOUS RUNTIME: Lives in {@code orazaka-apps/workers}. RabbitMQ is exclusively reserved
 * for persistent heavy jobs routed to the {@code orazaka.jobs.heavy} queue.
 */
@Configuration
class RuntimePoolConfig {

  /**
   * Synchronous executor for Chat/RAG SSE pipelines. Uses Project Loom virtual threads for
   * efficient blocking I/O handling.
   */
  @Bean("synchronousExecutor")
  ExecutorService synchronousExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
