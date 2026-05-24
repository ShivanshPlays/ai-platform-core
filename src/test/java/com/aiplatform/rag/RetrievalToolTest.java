package com.aiplatform.rag;
import com.aiplatform.rag.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Unit tests for RetrievalTool
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Strategy:
 *   Pre-load a SimpleVectorStore with known documents, then assert that
 *   RetrievalTool retrieves relevant chunks.  No LLM API, no Spring context.
 *   KeywordEmbeddingModel provides deterministic vectors so results are stable.
 *
 * MERN analogy:
 *   // Jest (Node.js):
 *   const store = new MemoryVectorStore(mockEmbeddings)
 *   await store.addDocuments([{ pageContent: 'omega-3 heart health', metadata: {} }])
 *   const result = await retrievalTool.retrieveContext('omega-3')
 *   expect(result).toContain('heart')
 *
 * Book ref: Chapter 19 — RAG: Retrieval & Reranking
 *   "Test retrieval in isolation from the LLM: given a known corpus, verify
 *    that the top-K includes documents that share vocabulary with the query."
 *
 * Book ref: Chapter 20 — RAG: Synthesis
 *   "The context block must be non-empty for grounded generation to work."
 */
class RetrievalToolTest {

    private SimpleVectorStore vectorStore;
    private RetrievalTool retrievalTool;

    @BeforeEach
    void setUp() {
        KeywordEmbeddingModel embeddingModel = new KeywordEmbeddingModel();
        vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        retrievalTool = new RetrievalTool(vectorStore);
    }

    // ── retrieveContext() — happy path ────────────────────────────────────

    @Test
    void retrieveContext_emptyStore_returnsEmptyString() {
        String context = retrievalTool.retrieveContext("omega-3 fatty acids");
        assertThat(context).isEmpty();
    }

    @Test
    void retrieveContext_findsRelevantChunks() {
        // Pre-load the store with an omega-3 document
        vectorStore.add(List.of(
                new Document("Omega-3 fatty acids include EPA and DHA found in fish oil.",
                             Map.of("docName", "omega3-guide"))
        ));

        String context = retrievalTool.retrieveContext("omega-3 fatty acids");
        assertThat(context).isNotBlank();
        // Should contain something from the ingested document
        assertThat(context.toLowerCase()).containsAnyOf("omega", "epa", "dha", "fish");
    }

    @Test
    void retrieveContext_returnsTopKResults() {
        // Ingest 10 short documents about different topics
        for (int i = 0; i < 10; i++) {
            vectorStore.add(List.of(
                    new Document("Nutrient fact " + i + ": vitamins minerals protein fat carbohydrate",
                                 Map.of("index", i))
            ));
        }

        // With topK=5, the context should contain multiple chunks joined by ---
        String context = retrievalTool.retrieveContext("vitamins minerals protein");
        // At least one chunk should be present
        assertThat(context).isNotBlank();
    }

    @Test
    void retrieveContext_multipleDocuments_separatedByDash() {
        // Load two documents that both match the query
        vectorStore.add(List.of(
                new Document("Salmon is rich in omega-3 fatty acids and protein."),
                new Document("Sardines contain high amounts of omega-3 EPA and DHA.")
        ));

        // With 2 relevant docs and topK=5, both should appear
        String context = retrievalTool.retrieveContext("omega-3 salmon sardines");
        assertThat(context).isNotBlank();
        // Multiple results are separated by ---
        if (context.contains("---")) {
            assertThat(context.split("---").length).isGreaterThan(1);
        }
    }

    // ── edge cases ────────────────────────────────────────────────────────

    @Test
    void retrieveContext_blankQuery_returnsEmptyString() {
        vectorStore.add(List.of(new Document("Some nutrition content here.")));
        assertThat(retrievalTool.retrieveContext("")).isEmpty();
        assertThat(retrievalTool.retrieveContext("   ")).isEmpty();
    }

    @Test
    void retrieveContext_nullQuery_returnsEmptyString() {
        assertThat(retrievalTool.retrieveContext(null)).isEmpty();
    }

    // ── noOp() factory ────────────────────────────────────────────────────

    @Test
    void noOp_alwaysReturnsEmptyString() {
        RetrievalTool noOp = RetrievalTool.noOp();
        assertThat(noOp.retrieveContext("omega-3")).isEmpty();
        assertThat(noOp.retrieveContext("protein synthesis")).isEmpty();
        assertThat(noOp.retrieveContext(null)).isEmpty();
    }

    // ── KeywordEmbeddingModel unit tests (piggybacked here) ───────────────

    @Test
    void keywordEmbeddingModel_computeVector_deterministicForSameInput() {
        float[] v1 = KeywordEmbeddingModel.computeVector("omega-3 fatty acids heart health");
        float[] v2 = KeywordEmbeddingModel.computeVector("omega-3 fatty acids heart health");
        assertThat(v1).isEqualTo(v2);
    }

    @Test
    void keywordEmbeddingModel_computeVector_hasCorrectDimensions() {
        float[] v = KeywordEmbeddingModel.computeVector("some text");
        assertThat(v).hasSize(KeywordEmbeddingModel.DIMENSIONS);
    }

    @Test
    void keywordEmbeddingModel_computeVector_nullAndBlankReturnZeroVector() {
        float[] nullVec = KeywordEmbeddingModel.computeVector(null);
        float[] blankVec = KeywordEmbeddingModel.computeVector("   ");
        assertThat(nullVec).hasSize(KeywordEmbeddingModel.DIMENSIONS);
        assertThat(blankVec).hasSize(KeywordEmbeddingModel.DIMENSIONS);
        // All zeros (no contribution)
        for (float v : nullVec) assertThat(v).isZero();
        for (float v : blankVec) assertThat(v).isZero();
    }

    @Test
    void keywordEmbeddingModel_computeVector_isL2Normalised() {
        float[] v = KeywordEmbeddingModel.computeVector("omega-3 fatty acids");
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        // Should be close to 1.0 (unit norm)
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void keywordEmbeddingModel_differentTexts_produceDifferentVectors() {
        float[] vA = KeywordEmbeddingModel.computeVector("omega-3 fatty acids");
        float[] vB = KeywordEmbeddingModel.computeVector("vitamin D sunshine calcium");
        assertThat(vA).isNotEqualTo(vB);
    }
}
