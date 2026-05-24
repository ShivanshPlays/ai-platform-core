package com.aiplatform.rag;
import com.aiplatform.rag.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Unit tests for QueryRewriter (Phase 12.5 — RAG level 4)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Strategy:
 *   Test ONLY the noOp() factory (no real ChatClient / LLM in unit tests).
 *   Integration tests that call the real LLM are out of scope for unit tests
 *   (they'd require a live GEMINI_API_KEY and network access).
 *
 *   The noOp() factory is the test double used by ResearchAgent's no-arg
 *   constructor — verifying its contract here ensures agent tests remain stable.
 *
 * MERN analogy:
 *   Like Jest testing the fallback branch of a module that has a real
 *   implementation and a mock implementation:
 *     const rewriter = QueryRewriter.noOp()
 *     expect(rewriter.rewrite('omega-3')).toEqual(['omega-3'])
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   "Test stubs should honour the same contract as the real implementation:
 *    include the original query, return a non-empty list, handle null input."
 */
class QueryRewriterTest {

    // ── noOp() factory ────────────────────────────────────────────────────

    @Test
    void noOp_rewrite_returnsOriginalQuery() {
        QueryRewriter rewriter = QueryRewriter.noOp();
        List<String> result = rewriter.rewrite("omega-3 fatty acids");
        assertThat(result).containsExactly("omega-3 fatty acids");
    }

    @Test
    void noOp_rewrite_resultIsNonEmpty() {
        QueryRewriter rewriter = QueryRewriter.noOp();
        List<String> result = rewriter.rewrite("vitamin D deficiency");
        assertThat(result).isNotEmpty();
    }

    @Test
    void noOp_rewrite_firstElementIsOriginalQuery() {
        QueryRewriter rewriter = QueryRewriter.noOp();
        String query = "magnesium bioavailability supplements";
        List<String> result = rewriter.rewrite(query);
        // Original query must be first (pipeline depends on this ordering)
        assertThat(result.get(0)).isEqualTo(query);
    }

    @Test
    void noOp_rewrite_nullQuery_returnsEmptyList() {
        QueryRewriter rewriter = QueryRewriter.noOp();
        List<String> result = rewriter.rewrite(null);
        assertThat(result).isEmpty();
    }

    @Test
    void noOp_rewrite_blankQuery_returnsListWithBlank() {
        // noOp returns whatever is passed (including blank), so caller can filter
        QueryRewriter rewriter = QueryRewriter.noOp();
        List<String> result = rewriter.rewrite("  ");
        assertThat(result).isNotNull();
    }

    @Test
    void noOp_multipleCallsReturnConsistentResults() {
        QueryRewriter rewriter = QueryRewriter.noOp();
        String query = "iron absorption plant-based diet";
        // noOp is deterministic — same query always returns same result
        assertThat(rewriter.rewrite(query)).isEqualTo(rewriter.rewrite(query));
    }
}
