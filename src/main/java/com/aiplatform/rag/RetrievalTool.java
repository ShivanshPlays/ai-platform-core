package com.aiplatform.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 10 — RetrievalTool (updated Phase 12.5: reranking + VectorStore iface)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Responsibility: given a query string, retrieve the top-K most relevant
 * document chunks from the VectorStore, rerank them (RAG level 3), and return
 * them as a single context block ready to inject into a prompt.
 *
 * PIPELINE POSITION (Phase 12.5):
 *   ResearchAgent.gatherFacts(query)
 *     └─► QueryRewriter.rewrite(query)              ← RAG level 4 (new)
 *           Result: [original, variant1, variant2]
 *     └─► For each query variant:
 *           RetrievalTool.retrieveContext(query)     ← this class
 *             └─► VectorStore.similaritySearch()    ← initial top-K retrieval
 *             └─► RerankingService.rerank()         ← RAG level 3 (new)
 *     └─► Inject combined context into LLM prompt
 *
 * VectorStore interface (Phase 12.5 change from SimpleVectorStore):
 *   Using the interface instead of the concrete SimpleVectorStore class means
 *   this tool works with BOTH backends:
 *     • SimpleVectorStore — H2/default profile (no DB needed, dev/test)
 *     • PgVectorStore     — local-pg profile  (PostgreSQL + pgvector)
 *   MERN analogy: coding to a repository interface, not the Mongoose Model class.
 *
 * Reranking (RAG level 3):
 *   After the initial cosine-similarity retrieval, a second scoring pass
 *   (BM25-style term overlap) re-orders the chunks for better precision.
 *   This improves the quality of the context block injected into the prompt.
 *
 * Book ref: Chapter 19 — RAG: Retrieval & Reranking
 *   "Top-K retrieval with cosine similarity is the baseline.  Reranking with
 *    a cross-encoder or BM25 scorer dramatically improves precision at small K."
 *
 * Book ref: Chapter 20 — RAG: Synthesis
 *   "Inject retrieved context in a clearly labelled section of the prompt so
 *    the model knows exactly which facts to use and doesn't hallucinate."
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Component
public class RetrievalTool {

    // Default top-K — fetches a slightly larger initial set so reranking has
    // more candidates to work with; final output is still topK chunks.
    private static final int DEFAULT_TOP_K = 5;

    // Sentinel: returned by noOp() instances so tests always get a clean state
    static final String NO_CONTEXT = "";

    private final VectorStore vectorStore;
    private final RerankingService rerankingService;
    private final int topK;

    /**
     * Spring-managed constructor — wires real VectorStore + RerankingService.
     * @Autowired needed because this class has multiple constructors.
     */
    @Autowired
    public RetrievalTool(VectorStore vectorStore, RerankingService rerankingService) {
        this.vectorStore = vectorStore;
        this.rerankingService = rerankingService;
        this.topK = DEFAULT_TOP_K;
    }

    // Package-visible constructor for tests that supply a custom topK or VectorStore
    public RetrievalTool(VectorStore vectorStore) {
        this(vectorStore, new RerankingService(), DEFAULT_TOP_K);
    }

    // Full-param constructor used by tests + noOp factory
    public RetrievalTool(VectorStore vectorStore, RerankingService rerankingService, int topK) {
        this.vectorStore = vectorStore;
        this.rerankingService = rerankingService;
        this.topK = topK;
    }

    /**
     * Retrieve the top-K relevant chunks for the query and join them into a
     * single string block separated by dashes.
     *
     * Returns an empty string when no documents have been ingested yet,
     * so callers can decide how to handle "no context" gracefully.
     *
     * MERN analogy:
     *   const docs = await vectorStore.similaritySearch(query, topK)
     *   return docs.map(d => d.pageContent).join('\n---\n')
     *
     * @param query the user's nutrition question / topic
     * @return retrieved context as a multi-paragraph string, or "" if empty
     *
     * Book ref: Chapter 19 — RAG: Retrieval & Reranking
     *   "At retrieval time, embed the query with the SAME model used at
     *    ingestion time — mismatched models destroy similarity scores."
     */
    public String retrieveContext(String query) {
        if (vectorStore == null || query == null || query.isBlank()) return NO_CONTEXT;

        // Step 1: Initial retrieval — fetch topK+5 candidates to give the reranker
        // a wider pool to work with.  Fetching extra candidates is a standard trick:
        // the reranker can elevate a chunk that scored lower in cosine distance but
        // has higher keyword overlap with the query.
        // MERN analogy: vectorStore.similaritySearch(query, topK + 5)
        int candidateK = topK + 5;
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(candidateK)
                .build();

        List<Document> candidates = vectorStore.similaritySearch(request);
        if (candidates == null || candidates.isEmpty()) return NO_CONTEXT;

        // Step 2: Reranking (RAG level 3) — re-score candidates using BM25-style
        // term-overlap scoring and return the top topK after reranking.
        //
        // Why rerank after cosine retrieval?
        //   Cosine similarity is a global semantic similarity — good for broad
        //   topical relevance.  BM25 term-frequency scoring is better for exact
        //   keyword matches.  Combining both (retrieve then rerank) gives higher
        //   precision than either alone.
        //
        // In a production system you'd use a cross-encoder model (e.g. Cohere
        // Rerank API) for even better quality.  The BM25-style scorer here is
        // a lightweight stand-in that teaches the same concept.
        //
        // MERN analogy: cohere.rerank({ documents: candidates, query, topN: topK })
        // Book ref: Chapter 19 — RAG: Retrieval & Reranking
        //   "A two-stage pipeline (retrieve many, rerank few) often outperforms
        //    a single-stage retrieval with a smaller K."
        List<Document> reranked = rerankingService.rerank(candidates, query, topK);

        // Step 3: Concatenate reranked chunks into a single context block.
        // The --- delimiter helps the LLM distinguish between source chunks.
        // Book ref: Ch 20 — label the context block so the model sees clear anchors.
        return reranked.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * No-op factory — returns a RetrievalTool instance backed by null stores.
     * Used by ResearchAgent's no-arg constructor so existing unit tests don't
     * need to wire a VectorStore.  retrieveContext() always returns "".
     *
     * MERN analogy: const mockRetrieval = { retrieveContext: () => '' }
     */
    public static RetrievalTool noOp() {
        return new RetrievalTool(null, new RerankingService(), DEFAULT_TOP_K) {
            @Override
            public String retrieveContext(String query) {
                return NO_CONTEXT;
            }
        };
    }
}
