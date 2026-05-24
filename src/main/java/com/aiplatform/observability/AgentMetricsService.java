package com.aiplatform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * AgentMetricsService — Phase 9 Observability: timing, MDC, and structured logging
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Provides two wrapping utilities:
 *
 *   timed()      — wraps an agent @Action call:
 *                    • Populates MDC with agentName, actionName, userId, promptVersion
 *                    • Records a Micrometer Timer (agent.action.duration)
 *                    • Emits a single structured log line with all fields
 *
 *   timedTool()  — wraps a tool method call:
 *                    • Populates MDC with toolName
 *                    • Records a Micrometer Timer (agent.tool.duration)
 *                    • Emits a structured log line (inputHash replaces raw input — PII safe)
 *
 * Why MDC?
 *   MDC (Mapped Diagnostic Context) attaches key-value pairs to the current
 *   thread's logging context.  Every log statement emitted inside a timed()
 *   block will automatically include agentName, userId, etc. — even if the
 *   log statement itself doesn't mention them.
 *   MERN/Next.js analogy: AsyncLocalStorage used as a request-scoped context
 *   in Node.js:
 *     const context = new AsyncLocalStorage()
 *     context.run({ userId, agentName }, () => {
 *       logger.info('doing work') // context is automatically included
 *     })
 *
 * Why Micrometer Timers?
 *   Micrometer is the default metrics library in Spring Boot (like Prometheus
 *   client or StatsD in Node.js).  Timers expose agent.action.duration
 *   and agent.tool.duration at /actuator/metrics, consumable by Grafana,
 *   Datadog, or any Micrometer-compatible backend.
 *   MERN analogy: prom-client or Datadog's dd-trace for recording latency metrics.
 *
 * hashInput():
 *   Tool inputs may contain user PII (names, dietary details).  This static
 *   helper produces a short hex fingerprint of the input using Java's
 *   hashCode() — sufficient for log correlation (not a security guarantee).
 *   MERN analogy: crypto.createHash('sha256').update(input).digest('hex').slice(0,8)
 *
 * noOp():
 *   A factory method that returns a fully functional instance backed by
 *   SimpleMeterRegistry (in-memory, discards metrics).  Used by unit tests
 *   to construct agents/tools without a Spring context.
 *   MERN analogy: a jest-compatible no-op metrics client.
 *
 * Book ref: Chapter 16 — Observability
 *   "To understand what your agents are doing in production, instrument
 *    every action and every tool call. Record latency, status, and a
 *    safe fingerprint of the input — never raw user data."
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   Structured per-action timing is the foundation for eval dashboards:
 *   you can correlate slow responses with specific agents or prompt versions.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Service
public class AgentMetricsService {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsService.class);

    // Micrometer MeterRegistry — auto-configured by spring-boot-starter-actuator.
    // MERN analogy: a Prometheus client or Datadog DogStatsD client.
    private final MeterRegistry meterRegistry;

    // ── MDC key constants ─────────────────────────────────────────────────
    // Keep keys consistent so dashboards and alerting rules don't break on rename.
    public static final String MDC_AGENT_NAME     = "agentName";
    public static final String MDC_ACTION_NAME    = "actionName";
    public static final String MDC_USER_ID        = "userId";
    public static final String MDC_PROMPT_VERSION = "promptVersion";
    public static final String MDC_TOOL_NAME      = "toolName";

    // ── Micrometer metric names ───────────────────────────────────────────
    public static final String METRIC_AGENT_DURATION = "agent.action.duration";
    public static final String METRIC_TOOL_DURATION  = "agent.tool.duration";

    public AgentMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // ── Factory ───────────────────────────────────────────────────────────

    /**
     * Returns a no-op instance backed by an in-memory registry.
     * Use this in unit tests that construct agents or tools without a Spring context.
     *
     * MERN analogy: a test-double metrics client that records nothing.
     */
    public static AgentMetricsService noOp() {
        return new AgentMetricsService(new SimpleMeterRegistry());
    }

    // ── Agent-level instrumentation ───────────────────────────────────────

    /**
     * Execute {@code task} and record timing, MDC, and a structured log event.
     *
     * Structured log fields (Book ref: Chapter 16):
     *   agentName     — the @Agent class handling this action
     *   actionName    — the @Action method name
     *   userId        — request-scoped user identifier
     *   durationMs    — wall-clock latency of the action
     *   status        — SUCCESS | ERROR
     *   promptVersion — the prompt template file name (e.g. "coach-system.st")
     *
     * @param agentName     e.g. "CoachAgent"
     * @param actionName    e.g. "advise"
     * @param userId        user identifier (safe to log)
     * @param promptVersion prompt template identifier (e.g. "coach-system.st")
     * @param task          the lambda to time (wraps AgentInvocation.invoke)
     * @return the value produced by {@code task}
     *
     * MERN analogy:
     *   const timed = async (agentName, actionName, userId, promptVersion, fn) => {
     *     const start = Date.now()
     *     try {
     *       const result = await fn()
     *       metrics.histogram('agent.action.duration', Date.now()-start, {agentName, status:'SUCCESS'})
     *       logger.info({ agentName, actionName, userId, durationMs: Date.now()-start, status:'SUCCESS' })
     *       return result
     *     } catch(e) {
     *       metrics.histogram('agent.action.duration', ..., {status:'ERROR'})
     *       throw e
     *     }
     *   }
     */
    public <T> T timed(String agentName, String actionName,
                       String userId, String promptVersion,
                       Supplier<T> task) {
        long start = System.currentTimeMillis();
        String status = "SUCCESS";

        MDC.put(MDC_AGENT_NAME,     agentName);
        MDC.put(MDC_ACTION_NAME,    actionName);
        MDC.put(MDC_USER_ID,        userId != null ? userId : "unknown");
        MDC.put(MDC_PROMPT_VERSION, promptVersion);

        try {
            return task.get();
        } catch (RuntimeException e) {
            status = "ERROR";
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            String finalStatus = status;

            // Micrometer timer — exposes agent.action.duration at /actuator/metrics
            Timer.builder(METRIC_AGENT_DURATION)
                 .tag("agentName",  agentName)
                 .tag("actionName", actionName)
                 .tag("status",     finalStatus)
                 .description("Wall-clock latency of Embabel @Action invocations")
                 .register(meterRegistry)
                 .record(durationMs, TimeUnit.MILLISECONDS);

            // Structured log event — machine-parseable key=value pairs
            // Book ref: Chapter 16 — "Log a single event per action, not chatty noise"
            log.info("agent.action agentName={} actionName={} userId={} durationMs={} status={} promptVersion={}",
                    agentName, actionName, userId, durationMs, finalStatus, promptVersion);

            // Clean up MDC so later log statements in this thread are not polluted.
            MDC.remove(MDC_AGENT_NAME);
            MDC.remove(MDC_ACTION_NAME);
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_PROMPT_VERSION);
        }
    }

    // ── Tool-level instrumentation ────────────────────────────────────────

    /**
     * Execute a tool call and record timing and a structured log event.
     *
     * The raw tool input is NEVER logged — only its hash (8-char hex fingerprint).
     * This prevents user PII (dietary restrictions, names, etc.) appearing in logs.
     *
     * Structured log fields:
     *   toolName  — e.g. "WebSearchTool.searchWeb"
     *   inputHash — short hex fingerprint of the raw input (see {@link #hashInput})
     *   durationMs — wall-clock latency
     *   status    — SUCCESS | ERROR
     *
     * @param toolName  logical name of the tool + method (e.g. "MemoryTool.lookupNotes")
     * @param inputHash produced by {@link #hashInput(String)} — never raw user data
     * @param task      the lambda to time (wraps the actual tool call)
     * @return the value produced by {@code task}
     *
     * MERN analogy:
     *   const timedTool = async (toolName, inputHash, fn) => {
     *     const start = Date.now()
     *     try {
     *       const result = await fn()
     *       metrics.histogram('agent.tool.duration', ..., {toolName, status:'SUCCESS'})
     *       logger.info({ toolName, inputHash, durationMs, status:'SUCCESS' })
     *       return result
     *     } catch(e) { ... throw e }
     *   }
     */
    public <T> T timedTool(String toolName, String inputHash, Supplier<T> task) {
        long start = System.currentTimeMillis();
        String status = "SUCCESS";

        MDC.put(MDC_TOOL_NAME, toolName);

        try {
            return task.get();
        } catch (RuntimeException e) {
            status = "ERROR";
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            String finalStatus = status;

            Timer.builder(METRIC_TOOL_DURATION)
                 .tag("toolName", toolName)
                 .tag("status",   finalStatus)
                 .description("Wall-clock latency of agent tool invocations")
                 .register(meterRegistry)
                 .record(durationMs, TimeUnit.MILLISECONDS);

            log.info("agent.tool toolName={} inputHash={} durationMs={} status={}",
                    toolName, inputHash, durationMs, finalStatus);

            MDC.remove(MDC_TOOL_NAME);
        }
    }

    // ── PII-safe input fingerprinting ─────────────────────────────────────

    /**
     * Returns an 8-character hex fingerprint of the input.
     *
     * This is NOT a cryptographic hash — it uses Java's hashCode(), which is
     * fast and sufficient for log correlation but not for security guarantees.
     * The intent is to identify WHICH call a log record refers to without
     * exposing the raw content (dietary restrictions, user names, etc.).
     *
     * @param input the raw tool input (e.g. the search query or userId+note)
     * @return 8-char hex string (e.g. "3f2a1b9c"), or "null" if input is null
     *
     * MERN analogy:
     *   const hashInput = (s: string) => crypto.createHash('md5').update(s).digest('hex').slice(0,8)
     *
     * Book ref: Chapter 16 — Observability
     *   "Hash user inputs before logging. Even a weak hash prevents raw PII
     *    from appearing in log aggregators like Datadog or CloudWatch."
     */
    public static String hashInput(String input) {
        if (input == null) return "null";
        // Use unsigned hex to avoid "-" signs from negative hashCodes
        return String.format("%08x", Integer.toUnsignedLong(input.hashCode()));
    }
}
