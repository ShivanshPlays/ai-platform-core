package com.aiplatform.observability;
import com.aiplatform.observability.AgentMetricsService;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * Unit tests for AgentMetricsService
 * ══════════════════════════════════════════════════════════════════════════
 *
 * No Spring context, no LLM, no DB — pure Java unit tests.
 * Uses SimpleMeterRegistry (in-memory, discards on GC) as the backing store.
 *
 * Testing strategy:
 *   1. timed() runs the task and returns its value
 *   2. timed() registers a Micrometer timer on success
 *   3. timed() registers a timer tagged status=ERROR on RuntimeException
 *   4. timed() cleans up MDC after completion (no context pollution)
 *   5. timedTool() registers a timer with the tool name tag
 *   6. hashInput() never returns null; stable deterministic output
 *   7. noOp() factory produces a working instance (no Spring context needed)
 *
 * MERN/Next.js analogy:
 *   Jest unit tests for a metrics wrapper utility:
 *     it('records latency on success', async () => {
 *       const { timed } = createMetrics(mockPrometheus)
 *       await timed('CoachAgent', 'advise', 'u1', 'v1', async () => 'result')
 *       expect(mockPrometheus.histogram).toHaveBeenCalledWith(
 *         'agent.action.duration', expect.any(Number), { agentName: 'CoachAgent', status: 'SUCCESS' }
 *       )
 *     })
 *
 * Book ref: Chapter 16 — Observability
 *   "Test your metrics wrappers in isolation. You want to know that a
 *    failed action always emits status=ERROR before you rely on dashboards."
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   The metrics service is itself part of the observability layer — testing it
 *   confirms that the eval infrastructure is trustworthy.
 * ══════════════════════════════════════════════════════════════════════════
 */
class AgentMetricsServiceTest {

    /**
     * Test Matrix — timed() and timedTool() inputs vs expected outcomes
     * ┌───────────────┬────────────────────────┬──────────────────────────┐
     * │ Input         │ Task outcome           │ Expected side-effect     │
     * ├───────────────┼────────────────────────┼──────────────────────────┤
     * │ timed()       │ Returns value normally │ Timer(status=SUCCESS)    │
     * │ timed()       │ Throws RuntimeException│ Timer(status=ERROR)      │
     * │ timedTool()   │ Returns value normally │ Timer(status=SUCCESS)    │
     * │ timedTool()   │ Throws RuntimeException│ Timer(status=ERROR)      │
     * └───────────────┴────────────────────────┴──────────────────────────┘
     */

    private SimpleMeterRegistry registry;
    private AgentMetricsService metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AgentMetricsService(registry);
        // Ensure MDC is clean before each test
        MDC.clear();
    }

    // ── timed() ───────────────────────────────────────────────────────────

    @Test
    void timed_successTask_returnsValue() {
        // Arrange
        String expected = "coach advice";

        // Act
        String result = metrics.timed("CoachAgent", "advise", "user1", "coach-system.st",
                () -> expected);

        // Assert — value passes through unchanged
        assertEquals(expected, result, "timed() must return the task's return value");
    }

    @Test
    void timed_successTask_registersSuccessTimer() {
        // Act
        metrics.timed("CoachAgent", "advise", "user1", "coach-system.st",
                () -> "result");

        // Assert — Micrometer timer with SUCCESS tag exists
        Timer timer = registry.find(AgentMetricsService.METRIC_AGENT_DURATION)
                .tag("agentName",  "CoachAgent")
                .tag("actionName", "advise")
                .tag("status",     "SUCCESS")
                .timer();

        assertNotNull(timer, "A timer with status=SUCCESS must be registered on success");
        assertEquals(1, timer.count(), "Timer must record exactly one observation");
    }

    @Test
    void timed_exceptionTask_registersErrorTimer_andRethrows() {
        // Act + Assert — exception re-thrown
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> metrics.timed("CoachAgent", "advise", "user1", "coach-system.st",
                        () -> { throw new RuntimeException("LLM failed"); }));

        assertEquals("LLM failed", ex.getMessage());

        // Assert — timer with ERROR tag registered
        Timer timer = registry.find(AgentMetricsService.METRIC_AGENT_DURATION)
                .tag("status", "ERROR")
                .timer();

        assertNotNull(timer, "A timer with status=ERROR must be registered on exception");
        assertEquals(1, timer.count(), "Timer must record exactly one observation");
    }

    @Test
    void timed_cleansUpMdcAfterSuccess() {
        // Arrange — pre-check that MDC is empty
        assertNull(MDC.get(AgentMetricsService.MDC_AGENT_NAME));

        // Act
        metrics.timed("CoachAgent", "advise", "user1", "coach-system.st",
                () -> "done");

        // Assert — MDC keys removed after timed() completes
        assertNull(MDC.get(AgentMetricsService.MDC_AGENT_NAME),
                "agentName must be removed from MDC after timed() completes");
        assertNull(MDC.get(AgentMetricsService.MDC_USER_ID),
                "userId must be removed from MDC after timed() completes");
    }

    @Test
    void timed_cleansUpMdcAfterException() {
        // Even when the task throws, MDC must be cleaned up.
        try {
            metrics.timed("CoachAgent", "advise", "user1", "coach-system.st",
                    () -> { throw new RuntimeException("boom"); });
        } catch (RuntimeException ignored) {}

        // Assert — MDC still clean despite exception
        assertNull(MDC.get(AgentMetricsService.MDC_AGENT_NAME),
                "MDC must be cleaned up even after an exception inside timed()");
    }

    // ── timedTool() ───────────────────────────────────────────────────────

    @Test
    void timedTool_successTask_registersSuccessTimer() {
        // Act
        String result = metrics.timedTool("WebSearchTool.searchWeb", "abc12345",
                () -> "search result");

        assertEquals("search result", result,
                "timedTool() must return the tool's return value");

        Timer timer = registry.find(AgentMetricsService.METRIC_TOOL_DURATION)
                .tag("toolName", "WebSearchTool.searchWeb")
                .tag("status",   "SUCCESS")
                .timer();

        assertNotNull(timer, "A tool timer with status=SUCCESS must be registered");
        assertEquals(1, timer.count());
    }

    @Test
    void timedTool_exceptionTask_registersErrorTimer() {
        assertThrows(RuntimeException.class,
                () -> metrics.timedTool("MemoryTool.lookupNotes", "xyz",
                        () -> { throw new RuntimeException("DB unavailable"); }));

        Timer timer = registry.find(AgentMetricsService.METRIC_TOOL_DURATION)
                .tag("toolName", "MemoryTool.lookupNotes")
                .tag("status",   "ERROR")
                .timer();

        assertNotNull(timer, "A tool timer with status=ERROR must be registered on exception");
    }

    // ── noOp() factory ────────────────────────────────────────────────────

    @Test
    void noOp_worksWithoutSpringContext() {
        // Verifies that noOp() can be used to construct tool/agent dependencies
        // in unit tests that have no Spring context.
        AgentMetricsService noOpMetrics = AgentMetricsService.noOp();

        // Should not throw and should return the task value
        String result = noOpMetrics.timed("TestAgent", "action", "user", "prompt.st",
                () -> "value");

        assertEquals("value", result, "noOp() instance must still return task values");
    }

    // ── hashInput() ───────────────────────────────────────────────────────

    @Test
    void hashInput_nonNull_returns8CharHex() {
        String hash = AgentMetricsService.hashInput("omega-3 supplements");

        assertNotNull(hash);
        assertEquals(8, hash.length(), "Hash must be exactly 8 hex characters");
        // Must be valid hex (matches [0-9a-f]{8})
        assertTrue(hash.matches("[0-9a-f]{8}"),
                "Hash must consist of lowercase hex digits, got: " + hash);
    }

    @Test
    void hashInput_nullInput_returnsLiteralNull() {
        assertEquals("null", AgentMetricsService.hashInput(null),
                "hashInput(null) must return the string 'null'");
    }

    @Test
    void hashInput_sameInput_deterministicResult() {
        String h1 = AgentMetricsService.hashInput("creatine monohydrate");
        String h2 = AgentMetricsService.hashInput("creatine monohydrate");

        assertEquals(h1, h2, "hashInput must be deterministic for the same input");
    }

    @Test
    void hashInput_differentInputs_differentHashes() {
        // Not guaranteed (hash collisions exist) but extremely unlikely for these inputs
        String h1 = AgentMetricsService.hashInput("creatine monohydrate");
        String h2 = AgentMetricsService.hashInput("omega-3 supplements");

        assertNotEquals(h1, h2,
                "Different inputs should (almost always) produce different hashes");
    }
}
