package com.aiplatform.eval;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 13 — EvalResult: single quality-gate check result
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Represents the outcome of one named eval assertion — pass or fail with a
 * human-readable detail message.  EvalService returns lists of these so a
 * test can inspect ALL results before deciding to fail, instead of stopping
 * at the first assertion error.
 *
 * MERN/Next.js analogy (Mastra eval result):
 *   // Mastra eval assertion (TypeScript):
 *   interface EvalResult {
 *     check:  string    // e.g. "schema", "grounding", "safety"
 *     passed: boolean
 *     detail: string    // "All fields present" | "Missing: keyFindings"
 *   }
 *
 * Design choice — value object over exception:
 *   EvalService methods return EvalResult instead of throwing assertions.
 *   This lets callers collect all failures at once (like jest's expect.soft()
 *   or a JUnit5 assertAll()) rather than stopping at the first failure.
 *   EvalService.assertAll() converts failing results into a single exception.
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   "Collect all eval results before reporting: a single test that shows
 *    three failing checks is more useful than three tests that each stop
 *    after the first failure."
 *
 * Book ref: Chapter 28 — Writing LLM Evals
 *   "An eval is just a function: input → pass/fail + reason.
 *    Keep the result type simple and composable."
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * @param check  Short name identifying the eval check (e.g. "schema:ResearchBrief")
 * @param passed Whether the check passed
 * @param detail Human-readable explanation (shown in failure messages)
 */
public record EvalResult(String check, boolean passed, String detail) {

    // ── Factory methods ───────────────────────────────────────────────────

    /**
     * Convenience factory for a passing result with a default "OK" detail.
     * MERN analogy: { check, passed: true, detail: 'OK' }
     */
    public static EvalResult pass(String check) {
        return new EvalResult(check, true, "OK");
    }

    /**
     * Convenience factory for a passing result with a custom detail message.
     */
    public static EvalResult pass(String check, String detail) {
        return new EvalResult(check, true, detail);
    }

    /**
     * Convenience factory for a failing result.
     * MERN analogy: { check, passed: false, detail: reason }
     */
    public static EvalResult fail(String check, String detail) {
        return new EvalResult(check, false, detail);
    }

    /**
     * Returns a short, readable summary — useful in JUnit failure messages.
     * MERN analogy: JSON.stringify(result) in a Jest error message.
     */
    @Override
    public String toString() {
        return "[" + (passed ? "PASS" : "FAIL") + "] " + check + " — " + detail;
    }
}
