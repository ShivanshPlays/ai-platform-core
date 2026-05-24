package com.aiplatform.guardrail;

import java.util.List;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * OutputModerator — Flag potentially unsafe advice before sending to client
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Scans all text fields of a CoachAdvice for patterns that indicate the LLM
 * has produced dangerous dietary or medical claims.  If found, throws
 * UnsafeOutputException which the controller translates to HTTP 422.
 *
 * Why output moderation?
 *   Even with a carefully crafted system prompt, LLMs can occasionally
 *   hallucinate harmful advice:
 *     • "Eat only 300 calories per day to lose weight fast."
 *     • "You can stop your diabetes medication if you follow this diet."
 *     • "This will cure your cancer."
 *   These statements, if delivered to users of a nutrition app, could cause
 *   serious physical harm.
 *
 * What is checked:
 *   UNSAFE_PATTERNS list covers:
 *     1. Specific medical claims — "cure/treat/diagnose your [condition]"
 *     2. Medication tampering   — "stop taking your [medication]"
 *     3. Dangerously low calorie targets — "eat fewer than 5xx calories"
 *
 * Limitations:
 *   Pattern matching catches obvious unsafe language but is not a complete
 *   safety solution.  CriticAgent (also in Phase 6) provides a semantic
 *   second opinion via the LLM itself for subtler issues.
 *
 * MERN/Next.js analogy:
 *   Equivalent of a response interceptor in a Mastra agent that runs
 *   before the output is returned to the client:
 *
 *     export const coachAgent = new Agent({
 *       afterGenerate: async (result) => {
 *         if (isMedicalClaim(result.text)) throw new SafetyViolation()
 *       }
 *     })
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   "Output moderation is the last line of defence. Never ship an agent
 *    without at least a keyword-level safety check on sensitive domains."
 * ══════════════════════════════════════════════════════════════════════════
 */
public class OutputModerator {

    // ── Unsafe output patterns ────────────────────────────────────────────────
    // OWASP LLM Top 10: LLM08 — Excessive Agency and unsafe output.
    // Subclasses can override getUnsafePatterns() to provide domain-specific patterns.
    private static final List<Pattern> DEFAULT_UNSAFE_PATTERNS = List.of(
            // Medical treatment claims — "cure/treat/diagnose your [condition]"
            Pattern.compile(
                    "\\b(cure|treat|diagnose|prescribe|medicate)s?\\b.{0,30}\\b(cancer|diabetes|disease|condition|disorder|illness)\\b",
                    CASE_INSENSITIVE),
            // Medication tampering — "stop taking your medication" / "stop taking your prescription drugs"
            Pattern.compile(
                    "\\bstop\\s+(?:taking\\s+)?(?:your\\s+)?(medication|prescription|insulin|drug|pill)s?\\b",
                    CASE_INSENSITIVE),
            // Dangerously low calorie target — "eat only 300 / fewer than 499 calories"
            Pattern.compile(
                    "(?:eat|consume|limit|only|fewer\\s+than|less\\s+than)\\s+(?:[1-4]\\d{2})\\s+cal",
                    CASE_INSENSITIVE)
    );

    /**
     * Returns the list of unsafe patterns to check against.
     * Subclasses can override to provide domain-specific patterns.
     */
    protected List<Pattern> getUnsafePatterns() {
        return DEFAULT_UNSAFE_PATTERNS;
    }

    /**
     * Check raw text for unsafe patterns.
     *
     * Used by streaming endpoints where output arrives as a single accumulated
     * string rather than a typed CoachAdvice record.
     *
     * MERN analogy:
     *   function moderateStream(fullText) {
     *     if (UNSAFE_PATTERNS.some(p => p.test(fullText))) throw new SafetyError()
     *   }
     *
     * Book ref: Chapter 9 — Middleware & Guardrails
     *   "Apply output moderation to the buffered full response even when
     *    streaming — buffer server-side, check, then re-emit."
     *
     * @param text the full text to moderate
     * @throws UnsafeOutputException if any unsafe pattern is found
     */
    public void checkText(String text) {
        if (text == null || text.isBlank()) return;

        for (Pattern pattern : getUnsafePatterns()) {
            if (pattern.matcher(text).find()) {
                throw new UnsafeOutputException(
                        "Output moderation failed: potentially unsafe content detected in stream");
            }
        }
    }
}
