package com.aiplatform.rag;
import com.aiplatform.rag.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Unit tests for DocumentIngestionService
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Strategy:
 *   Use a real SimpleVectorStore backed by KeywordEmbeddingModel — both are
 *   pure in-memory, no LLM API calls, no Spring context needed.
 *   This tests the full ingest → store path end-to-end.
 *
 * MERN analogy:
 *   Like using MemoryVectorStore in a Jest test to verify that
 *   LangChain.js addDocuments() actually stores the chunks:
 *     const store = new MemoryVectorStore(mockEmbeddings)
 *     await service.ingest(store, docName, content)
 *     const results = await store.similaritySearch('query')
 *     expect(results.length).toBeGreaterThan(0)
 *
 * Book ref: Chapter 17 — RAG: Chunking & Ingestion
 *   "Test that ingestion produces chunks, that chunks are stored, and that they
 *    are retrievable. A failing ingest is silent without these checks."
 */
class DocumentIngestionServiceTest {

    private KeywordEmbeddingModel embeddingModel;
    private SimpleVectorStore vectorStore;
    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        embeddingModel = new KeywordEmbeddingModel();
        vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        service = new DocumentIngestionService(vectorStore);
    }

    // ── ingestText() ──────────────────────────────────────────────────────

    @Test
    void ingestText_returnsCorrectDocName() {
        var result = service.ingestText("omega3-guide", sampleContent());
        assertThat(result.docName()).isEqualTo("omega3-guide");
    }

    @Test
    void ingestText_returnsPositiveChunkCount() {
        var result = service.ingestText("omega3-guide", sampleContent());
        // The splitter should produce at least one chunk for any non-trivial content
        assertThat(result.chunkCount()).isGreaterThan(0);
    }

    @Test
    void ingestText_shortContent_returnsSingleChunk() {
        // Very short content (< min chunk size) should still produce at least one chunk
        var result = service.ingestText("short-doc", "Omega-3 is good for the heart.");
        assertThat(result.chunkCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void ingestText_longContent_producesMultipleChunks() {
        // Build a long string that exceeds the default chunk size (~800 tokens)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("Omega-3 fatty acids are polyunsaturated fats found in fish oil. ")
              .append("EPA and DHA are the two main forms of omega-3 found in fish and seafood. ")
              .append("ALA is a plant-based omega-3 found in flaxseed, chia seeds, and walnuts. ")
              .append("Regular consumption of omega-3 fatty acids has been associated with reduced ");
        }
        var result = service.ingestText("long-doc", sb.toString());
        assertThat(result.chunkCount()).isGreaterThan(1);
    }

    @Test
    void ingestText_chunksAreRetrievable() {
        // Ingest a document, then verify it is discoverable via similarity search
        service.ingestText("omega3-guide", sampleContent());

        // Search should return at least one result (not empty)
        var results = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query("omega-3 fatty acids heart health")
                        .topK(5)
                        .build()
        );
        assertThat(results).isNotEmpty();
    }

    // ── ingestAll() ───────────────────────────────────────────────────────

    @Test
    void ingestAll_ingestsAllDocuments() {
        var rawDocs = List.of(
                new DocumentIngestionService.RawDocument("doc-a", "Vitamin D supports bone health and immune function."),
                new DocumentIngestionService.RawDocument("doc-b", "Magnesium is essential for muscle contraction and nerve signalling.")
        );

        var results = service.ingestAll(rawDocs);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).docName()).isEqualTo("doc-a");
        assertThat(results.get(1).docName()).isEqualTo("doc-b");
        assertThat(results).allMatch(r -> r.chunkCount() >= 1);
    }

    @Test
    void ingestAll_emptyList_returnsEmptyResults() {
        var results = service.ingestAll(List.of());
        assertThat(results).isEmpty();
    }

    // ── helper ────────────────────────────────────────────────────────────

    private String sampleContent() {
        return """
                Omega-3 fatty acids are polyunsaturated fatty acids that are essential to human health.
                The three most important types are EPA (eicosapentaenoic acid), DHA (docosahexaenoic acid),
                and ALA (alpha-linolenic acid).

                EPA and DHA are primarily found in fatty fish such as salmon, mackerel, and sardines.
                ALA is primarily found in plant sources such as flaxseeds, chia seeds, and walnuts.

                Health Benefits:
                - Cardiovascular: omega-3 fatty acids reduce triglycerides and inflammatory markers.
                - Brain health: DHA is a structural component of the brain and retina.
                - Anti-inflammatory: EPA reduces production of inflammatory eicosanoids.

                Recommended dosage for adults: 250–500mg EPA+DHA per day as a minimum.
                Therapeutic doses for specific conditions may be 1–4g/day, supervised by a doctor.
                """;
    }
}
