package com.aiplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * AsyncConfig — enable Spring's @Async task execution
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Phase 8: activates Spring's asynchronous method execution support.
 * Without @EnableAsync, methods annotated with @Async run synchronously
 * in the calling thread — @EnableAsync tells Spring to proxy them and
 * dispatch execution to a thread pool instead.
 *
 * MERN/Next.js analogy:
 *   In Node.js, everything is async by default (event loop + Promises).
 *   In Java/Spring, methods are synchronous unless you opt in with @Async
 *   and @EnableAsync.  This is roughly equivalent to:
 *     setImmediate(() => loggerService.run(userId, messages))
 *   — fire-and-forget, doesn't block the HTTP response.
 *
 * Thread pool:
 *   Spring uses a SimpleAsyncTaskExecutor by default when no custom
 *   Executor bean is defined.  This is fine for Phase 8; Phase 9
 *   (Observability) will optionally replace it with a named
 *   ThreadPoolTaskExecutor for better metrics and back-pressure control.
 *
 * Book ref: Chapter 8 — Dynamic Agents
 *   "Schedule background agents to run asynchronously after each
 *    user turn so they add zero latency to the user-facing response."
 * ══════════════════════════════════════════════════════════════════════════
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // No bean definitions needed here — @EnableAsync is sufficient.
    // A custom Executor bean can be added in Phase 9 for observability.
}
