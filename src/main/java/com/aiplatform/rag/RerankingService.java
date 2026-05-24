package com.aiplatform.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 12.5 — RerankingService (RAG level 3)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Responsibility: given a list of candidate documents (retrieved by cosine
 * similarity from the VectorStore) and the original query, re-score the
 * candidates using BM25-style term-overlap scoring and return the top-N.
 *
 * WHY RERANK?
 *   The initial retrieval step (VectorStore.similaritySearch) uses the
 *   embedding model's cosine similarity — excellent for broad semantic
 *   topical relevance ("this chunk is about nutrition").
 *
 *   But cosine similarity can miss exact keyword matches when the query has
 *   specific terms (e.g., "magnesium absorption in the gut") that happen to
 *   hash to low-frequency vector dimensions.  A BM25-style term-frequency
 *   scorer catches these exact matches and promotes them to the top.
 *
 *   The two-stage pattern (retrieve-many → rerank-few) is the RAG industry
 *   standard and outperforms single-stage retrieval at small K values.
 *
 * ALGORITHM — BM25-simplified Jaccard overlap:
 *   1. Tokenise query into a set of non-trivial terms (length > 2, lowercase)
 *   2. For each candidate document, tokenise its content the same way
 *   3. Score = |query_terms ∩ doc_terms| / |query_terms ∪ doc_terms|
 *      (Jaccard similarity — bounded [0, 1], higher = more overlap)
 *   4. Sort descending by score; return top-N
 *
 *   Note: This is a simplified approximation of BM25, which additionally
 *   weights by term frequency (TF) and inverse document frequency (IDF).
 *   A full BM25 implementation would require a document index, which is
 *   beyond the scope of this learning project.  The Jaccard approximation
 *   captures the key insight: shared vocabulary → high relevance.
 *
 * PRODUCTION ALTERNATIVE:
 *   Replace this service with a cross-encoder reranker for higher quality:
 *   • Cohere Rerank API  — HTTP call, no local model needed
 *   • HuggingFace cross-encoder (ONNX via TransformersEmbeddingModel)
 *   • Jina AI Reranker   — REST API, developer tier free
 *
 *   A cross-encoder reads (query, document) as a pair and produces a score —
 *   much more accurate than independent query/document encoders (bi-encoders).
 *   The cost is ~3x slower than this BM25 approach.
 *
 * MERN/Next.js analogy:
 *   Like using Cohere's reranker in a LangChain.js pipeline:
 *     const reranked = await cohere.rerank({ query, documents: candidates, topN: k })
 *   Or a custom scorer:
 *     const scored = candidates.map(doc => ({ doc, score: bm25Score(query, doc.text) }))
 *     return scored.sort((a, b) => b.score - a.score).slice(0, topN).map(s => s.doc)
 *
 * Book ref: Chapter 19 — RAG: Retrieval & Reranking
 *   "A two-stage pipeline (retrieve many, rerank few) often outperforms a
 *    single-stage retrieval with a smaller K. The reranker acts as a precision
 *    filter: it can elevate a chunk ranked 8th by cosine to first place if
 *    it has strong exact keyword overlap with the query."
 *
 * Book ref: Chapter 19 — RAG: Retrieval & Reranking
 *   "Use cosine similarity (bi-encoder) for recall, cross-encoder for precision.
 *    In resource-constrained environments, BM25 is a strong baseline reranker."
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Service
public class RerankingService {

    // Minimum token length to include in scoring vocabulary.
    // Short tokens ("is", "of", "a") are stop-word noise; filtering them
    // improves precision.  3 chars is a common threshold.
    private static final int MIN_TOKEN_LENGTH = 3;

    /**
     * Rerank a list of candidate documents by BM25-style term overlap and
     * return the top-N.
     *
     * @param candidates documents retrieved by cosine similarity (initial top-K)
     * @param query      the original user query (same string used for retrieval)
     * @param topN       maximum number of documents to return after reranking
     * @return reranked documents, best match first, length ≤ topN
     *
     * MERN analogy:
     *   function rerank(candidates: Document[], query: string, topN: number): Document[] {
     *     const queryTerms = tokenize(query)
     *     return candidates
     *       .map(d => ({ doc: d, score: jaccardScore(tokenize(d.text), queryTerms) }))
     *       .sort((a, b) => b.score - a.score)
     *       .slice(0, topN)
     *       .map(s => s.doc)
     *   }
     */
    public List<Document> rerank(List<Document> candidates, String query, int topN) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (query == null || query.isBlank()) return candidates.stream().limit(topN).toList();

        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            // Fall back to original cosine-similarity order if query has no scoreable terms
            return candidates.stream().limit(topN).toList();
        }

        // Score each candidate; sort descending; return top-N
        // MERN analogy: candidates.map(d => ({ ...d, score: jaccardScore(d, queryTerms) }))
        return candidates.stream()
                .sorted(Comparator.comparingDouble(
                        (Document doc) -> jaccardScore(tokenize(doc.getText()), queryTerms)
                ).reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }

    /**
     * Jaccard similarity between two token sets.
     *
     *   J(A, B) = |A ∩ B| / |A ∪ B|
     *
     * Range: [0.0, 1.0]. Score of 1.0 means the document only contains query
     * terms (and vice versa). Score of 0.0 means no shared vocabulary.
     *
     * Package-visible for unit testing.
     *
     * Book ref: Chapter 19 — RAG: Retrieval & Reranking
     *   "Jaccard is a simple but effective baseline: it captures vocabulary
     *    overlap without requiring a document index or IDF weights."
     */
    public double jaccardScore(Set<String> docTerms, Set<String> queryTerms) {
        if (docTerms.isEmpty() || queryTerms.isEmpty()) return 0.0;

        // |A ∩ B| = count terms that appear in both sets
        long intersection = queryTerms.stream().filter(docTerms::contains).count();
        // |A ∪ B| = |A| + |B| - |A ∩ B|  (inclusion-exclusion principle)
        long union = docTerms.size() + queryTerms.size() - intersection;

        return union == 0 ? 0.0 : (double) intersection / union;
    }

    /**
     * Normalise and tokenise text into a set of non-trivial terms.
     *
     * Steps:
     *   1. Lowercase — case-insensitive matching
     *   2. Strip non-alphanumeric characters — punctuation is not vocabulary
     *   3. Split on whitespace
     *   4. Filter tokens shorter than MIN_TOKEN_LENGTH — remove stop-word noise
     *
     * Returns a Set (not List) so duplicate tokens don't inflate scores.
     * Package-visible for unit testing.
     *
     * MERN analogy:
     *   const tokenize = (text: string) =>
     *     new Set(text.toLowerCase().replace(/[^a-z0-9\s]/g, ' ').split(/\s+/)
     *       .filter(t => t.length >= 3))
     */
    public Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(
                        text.toLowerCase()
                            .replaceAll("[^a-z0-9\\s]", " ")
                            .split("\\s+"))
                .filter(t -> t.length() >= MIN_TOKEN_LENGTH)
                .collect(Collectors.toSet());
    }
}
