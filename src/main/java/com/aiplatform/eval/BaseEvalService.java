package com.aiplatform.eval;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * BaseEvalService — Platform-generic eval assertion helpers
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Provides domain-agnostic eval checks that any AI product can reuse:
 *   1. GROUNDING   — key claims share vocabulary with retrieved context
 *   2. TOOL COVERAGE — expected tool section markers appear in the prompt
 *   3. LENGTH      — field length is within configured min/max character bounds
 *   4. ASSERT ALL  — aggregates multiple results into a single assertion
 *
 * Domain-specific checks (e.g. checkSchema(CoachAdvice)) belong in subclasses.
 *
 * MERN/Next.js analogy:
 *   // Base eval suite in packages/core/eval.ts:
 *   export const checkGrounding = (context, claims) => { ... }
 *   export const checkLength = (field, text, min, max) => { ... }
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   "An eval system has three layers:
 *    1. Schema / I/O contract  (domain-specific)
 *    2. Factual grounding      (generic — does output reference the context?)
 *    3. Safety / policy        (domain-specific patterns, generic mechanism)"
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class BaseEvalService {

    // ── Common English stop-words excluded from grounding token matching ──
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to",
            "for", "of", "with", "is", "are", "was", "were", "be", "been",
            "by", "from", "that", "this", "it", "its", "not", "no", "do",
            "does", "did", "has", "have", "had", "as", "up", "if", "into",
            "so", "can", "will", "may", "would", "could", "should", "each"
    );

    // Minimum token length considered meaningful for grounding checks
    private static final int MIN_TOKEN_LENGTH = 3;

    // ═══════════════════════════════════════════════════════════════════════
    // GROUNDING CHECK
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Checks that at least {@code minMatchRequired} of the supplied claims share
     * at least one meaningful token with the retrieved context string.
     */
    public EvalResult checkGrounding(String retrievedContext, List<String> claims, int minMatchRequired) {
        if (retrievedContext == null || retrievedContext.isBlank()) {
            return EvalResult.fail("grounding",
                    "Retrieved context is empty — grounding cannot be established");
        }
        if (claims == null || claims.isEmpty()) {
            return EvalResult.fail("grounding", "No claims provided to check");
        }

        Set<String> contextTokens = tokenize(retrievedContext);
        long groundedCount = claims.stream()
                .filter(claim -> !tokenize(claim).stream()
                        .filter(contextTokens::contains)
                        .toList().isEmpty())
                .count();

        if (groundedCount < minMatchRequired) {
            return EvalResult.fail("grounding",
                    "%d/%d claims grounded in context (required: %d)"
                            .formatted(groundedCount, claims.size(), minMatchRequired));
        }
        return EvalResult.pass("grounding",
                "%d/%d claims grounded in retrieved context"
                        .formatted(groundedCount, claims.size()));
    }

    /**
     * Convenience overload: at least half of the claims must be grounded.
     */
    public EvalResult checkGrounding(String retrievedContext, List<String> claims) {
        int required = Math.max(1, (int) Math.ceil(claims.size() / 2.0));
        return checkGrounding(retrievedContext, claims, required);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TOOL COVERAGE CHECK
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Checks that all expected tool-output section markers appear in the prompt string.
     */
    public EvalResult checkToolCoverage(String prompt, String... expectedMarkers) {
        if (prompt == null || prompt.isBlank()) {
            return EvalResult.fail("tool-coverage", "Prompt is null or empty");
        }
        List<String> missing = Arrays.stream(expectedMarkers)
                .filter(marker -> !prompt.contains(marker))
                .toList();
        if (!missing.isEmpty()) {
            return EvalResult.fail("tool-coverage",
                    "Missing tool markers in prompt: " + missing);
        }
        return EvalResult.pass("tool-coverage",
                "All %d tool markers present".formatted(expectedMarkers.length));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LENGTH CHECK
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Checks that a text field's character length is within [minChars, maxChars].
     */
    public EvalResult checkLength(String fieldName, String text, int minChars, int maxChars) {
        if (text == null) {
            return EvalResult.fail("length:" + fieldName, fieldName + " is null");
        }
        int len = text.length();
        if (len < minChars) {
            return EvalResult.fail("length:" + fieldName,
                    "%s too short: %d chars (minimum %d)".formatted(fieldName, len, minChars));
        }
        if (len > maxChars) {
            return EvalResult.fail("length:" + fieldName,
                    "%s too long: %d chars (maximum %d)".formatted(fieldName, len, maxChars));
        }
        return EvalResult.pass("length:" + fieldName,
                "%s: %d chars (within [%d, %d])".formatted(fieldName, len, minChars, maxChars));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ASSERT ALL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Throws {@link EvalAssertionError} if any result in the list failed.
     */
    public void assertAll(List<EvalResult> results) {
        List<EvalResult> failures = results.stream()
                .filter(r -> !r.passed())
                .toList();
        if (!failures.isEmpty()) {
            String message = failures.stream()
                    .map(EvalResult::toString)
                    .collect(Collectors.joining("\n  ", "Eval failures:\n  ", ""));
            throw new EvalAssertionError(message);
        }
    }

    /**
     * Varargs overload of {@link #assertAll(List)}.
     */
    public void assertAll(EvalResult... results) {
        assertAll(Arrays.asList(results));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private static Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> t.length() >= MIN_TOKEN_LENGTH)
                .filter(t -> !STOP_WORDS.contains(t))
                .collect(Collectors.toSet());
    }
}
