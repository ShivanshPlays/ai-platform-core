package com.aiplatform.rag;
import com.aiplatform.rag.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Unit tests for RerankingService (Phase 12.5 — RAG level 3)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Strategy:
 *   Pure unit tests — no Spring context, no LLM, no VectorStore.
 *   RerankingService has no external dependencies; all inputs are in-process.
 *   Tests verify the BM25-style Jaccard scoring and reranking logic.
 *
 * MERN analogy:
 *   Like Jest unit tests for a pure function:
 *     test('reranks by keyword overlap', () => {
 *       const ranked = rerank([doc1, doc2, doc3], 'omega-3 fish heart', 2)
 *       expect(ranked[0].text).toContain('omega-3')
 *     })
 *
 * Book ref: Chapter 19 — RAG: Retrieval & Reranking
 *   "Test the reranker in isolation: given a known corpus and query, verify
 *    that the document with the most vocabulary overlap ranks highest."
 */
class RerankingServiceTest {

    private RerankingService service;

    @BeforeEach
    void setUp() {
        service = new RerankingService();
    }

    // ── rerank() ─────────────────────────────────────────────────────────

    @Test
    void rerank_emptyList_returnsEmpty() {
        List<Document> result = service.rerank(List.of(), "omega-3", 5);
        assertThat(result).isEmpty();
    }

    @Test
    void rerank_nullList_returnsEmpty() {
        List<Document> result = service.rerank(null, "omega-3", 5);
        assertThat(result).isEmpty();
    }

    @Test
    void rerank_blankQuery_returnsTopNUnchanged() {
        List<Document> docs = List.of(
                new Document("doc1"),
                new Document("doc2"),
                new Document("doc3")
        );
        List<Document> result = service.rerank(docs, "", 2);
        assertThat(result).hasSize(2);
    }

    @Test
    void rerank_topNLargerThanList_returnsAllDocuments() {
        List<Document> docs = List.of(
                new Document("Omega-3 fatty acids EPA DHA fish oil"),
                new Document("Vitamin D sunshine deficiency bone health")
        );
        List<Document> result = service.rerank(docs, "omega-3 fish", 10);
        assertThat(result).hasSize(2);
    }

    @Test
    void rerank_highOverlapDocumentRanksFirst() {
        // doc1 shares only 1 token with the query
        Document lowOverlap = new Document("Vitamin D is important for bone health and immune function.");
        // doc2 shares multiple tokens with the query
        Document highOverlap = new Document("Omega-3 fatty acids EPA and DHA are found in fish oil and salmon.");

        List<Document> docs = List.of(lowOverlap, highOverlap);
        List<Document> result = service.rerank(docs, "omega-3 EPA DHA fish salmon", 2);

        // The document about omega-3 should be ranked first
        assertThat(result.get(0).getText()).containsIgnoringCase("omega");
    }

    @Test
    void rerank_respectsTopNLimit() {
        List<Document> docs = List.of(
                new Document("omega-3 fish oil EPA DHA heart health"),
                new Document("omega-3 fatty acids brain cognitive function"),
                new Document("omega-3 inflammation anti-inflammatory eicosanoids"),
                new Document("vitamin D sunshine calcium bone density"),
                new Document("magnesium enzyme cofactor ATP muscle")
        );
        List<Document> result = service.rerank(docs, "omega-3 health", 3);
        assertThat(result).hasSize(3);
    }

    @Test
    void rerank_allSameScore_returnsTopN() {
        // All docs have the same tokens — reranker shouldn't fail
        List<Document> docs = List.of(
                new Document("abc def ghi"),
                new Document("abc def ghi"),
                new Document("abc def ghi")
        );
        List<Document> result = service.rerank(docs, "xyz uvw", 2);
        // No overlap with query — all score 0, but still returns topN
        assertThat(result).hasSize(2);
    }

    // ── jaccardScore() ────────────────────────────────────────────────────

    @Test
    void jaccardScore_identicalSets_returnsOne() {
        Set<String> terms = Set.of("omega", "fish", "epa");
        double score = service.jaccardScore(terms, terms);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void jaccardScore_disjointSets_returnsZero() {
        Set<String> docTerms = Set.of("omega", "fish", "epa");
        Set<String> queryTerms = Set.of("vitamin", "calcium", "bone");
        double score = service.jaccardScore(docTerms, queryTerms);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void jaccardScore_partialOverlap_returnsBetweenZeroAndOne() {
        Set<String> docTerms   = Set.of("omega", "fish", "epa", "vitamin");
        Set<String> queryTerms = Set.of("omega", "epa", "calcium", "bone");
        // intersection = {omega, epa} = 2; union = {omega, fish, epa, vitamin, calcium, bone} = 6
        // expected = 2/6 ≈ 0.333
        double score = service.jaccardScore(docTerms, queryTerms);
        assertThat(score).isBetween(0.0, 1.0);
        assertThat(score).isCloseTo(2.0 / 6.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void jaccardScore_emptyDocTerms_returnsZero() {
        double score = service.jaccardScore(Set.of(), Set.of("omega", "fish"));
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void jaccardScore_emptyQueryTerms_returnsZero() {
        double score = service.jaccardScore(Set.of("omega", "fish"), Set.of());
        assertThat(score).isEqualTo(0.0);
    }

    // ── tokenize() ────────────────────────────────────────────────────────

    @Test
    void tokenize_filtersShortTokens() {
        // "is", "a", "of" should be filtered (length < 3)
        Set<String> tokens = service.tokenize("omega-3 is a source of EPA");
        assertThat(tokens).doesNotContain("is", "a", "of");
        assertThat(tokens).containsAnyOf("omega", "source", "epa");
    }

    @Test
    void tokenize_lowercasesInput() {
        Set<String> tokens = service.tokenize("Omega-3 EPA DHA Fish");
        assertThat(tokens).doesNotContainAnyElementsOf(List.of("Omega", "EPA", "DHA", "Fish"));
        assertThat(tokens).containsAnyOf("omega", "epa", "dha", "fish");
    }

    @Test
    void tokenize_stripsPunctuation() {
        Set<String> tokens = service.tokenize("omega-3, EPA/DHA (fish oil).");
        // Punctuation stripped; alphanumeric tokens remain
        assertThat(tokens).containsAnyOf("omega", "epa", "dha", "fish", "oil");
    }

    @Test
    void tokenize_nullOrBlank_returnsEmptySet() {
        assertThat(service.tokenize(null)).isEmpty();
        assertThat(service.tokenize("")).isEmpty();
        assertThat(service.tokenize("   ")).isEmpty();
    }

    @Test
    void tokenize_deduplicatesTokens() {
        // "omega" repeated twice should appear once in the set
        Set<String> tokens = service.tokenize("omega omega omega fish fish");
        assertThat(tokens.stream().filter("omega"::equals).count()).isEqualTo(1);
    }
}
