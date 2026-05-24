package com.aiplatform.eval;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 13 — EvalAssertionError
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Thrown by {@link EvalService#assertAll} when one or more eval checks fail.
 * Extends {@link AssertionError} so JUnit treats it the same as a native
 * assertion failure — the test fails with the collected failure messages.
 *
 * Why AssertionError (not RuntimeException)?
 *   JUnit 5 treats AssertionError specially: the failure message is shown
 *   inline in the test report without requiring a full stack trace to be
 *   expanded.  This makes CI output much easier to scan.
 *
 * MERN analogy:
 *   // Jest custom matcher:
 *   throw new JestAssertionError(failures.join('\n'))
 *   // — or — in Vitest:
 *   throw new AssertionError({ message: failures.join('\n') })
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   "Eval failures should be reported like test failures: the CI pipeline
 *    sees a non-zero exit code and blocks the merge."
 */
public class EvalAssertionError extends AssertionError {

    public EvalAssertionError(String message) {
        super(message);
    }
}
