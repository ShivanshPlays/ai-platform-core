package com.aiplatform.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 10 — Vector store configuration
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Why a @Configuration class and not auto-configuration?
 *   Spring AI does NOT auto-configure SimpleVectorStore.  Only specific
 *   backends (PgVector, Chroma, Pinecone, etc.) get auto-configured via their
 *   own starters.  For SimpleVectorStore we provide the bean explicitly.
 *
 * VectorStore interface (Spring AI):
 *   • add(List<Document>)            — index new documents
 *   • similaritySearch(SearchRequest)— top-K retrieval
 *   • delete(List<String>)           — remove by ID
 *   MERN analogy: a Pinecone / Weaviate / Supabase Vector client providing
 *   the same upsert/query/delete API.
 *
 * SimpleVectorStore:
 *   An in-memory implementation — perfect for dev and automated tests.
 *   Every document is stored in a ConcurrentHashMap; similarity search
 *   does a brute-force cosine comparison over all stored vectors.
 *   Data is lost on application restart; no schema migration needed.
 *
 * SWITCHING TO PgVectorStore (Phase 10, level 2):
 *   1. Uncomment the PostgreSQL driver in pom.xml
 *   2. Un-comment the pgvector starter below (once added to pom.xml):
 *        spring-ai-pgvector-store-spring-boot-starter
 *   3. Replace the @Bean below with:
 *        @Bean
 *        public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel em) {
 *            return new PgVectorStore(jdbcTemplate, em);
 *        }
 *   4. Add Flyway V4 migration:
 *        CREATE EXTENSION IF NOT EXISTS vector;
 *        CREATE TABLE vector_store (
 *            id UUID PRIMARY KEY,
 *            content TEXT,
 *            metadata JSON,
 *            embedding vector(384)
 *        );
 *        CREATE INDEX ON vector_store USING ivfflat (embedding vector_cosine_ops);
 *   5. Switch datasource URL in application.yml to PostgreSQL
 *
 * MERN analogy:
 *   // Supabase (Node.js) — switching from memory to pgvector:
 *   const vectorStore = await SupabaseVectorStore.fromDocuments(docs, embeddings, { client })
 *   // vs in-memory:
 *   const vectorStore = await MemoryVectorStore.fromDocuments(docs, embeddings)
 *
 * Book ref: Chapter 18 — RAG: Embedding & Indexing
 *   "The vector DB is where embedding vectors live at query-time.
 *    Start with in-memory for development; switch to pgvector for production
 *    persistence and approximate nearest-neighbour (ANN) indexing."
 *
 * Book ref: Chapter 19 — RAG: Retrieval & Reranking
 *   "SimpleVectorStore uses exact cosine similarity (O(n)).
 *    PgVector with ivfflat index uses ANN (O(log n)) for large corpora."
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Configuration
public class VectorStoreConfig {

    /**
     * In-memory vector store — active in the default (H2) profile.
     *
     * @Profile("!local-pg") ensures this bean is ONLY created when NOT running
     * with the local-pg profile.  In local-pg mode, the PgVectorStore bean below
     * takes over.  Both implement the VectorStore interface so the rest of the
     * application (DocumentIngestionService, RetrievalTool) is unaffected.
     *
     * MERN analogy: conditional default export:
     *   export const vectorStore = process.env.USE_PG
     *     ? new PgVectorStore(config)
     *     : new MemoryVectorStore(embeddingModel)
     */
    @Bean
    @Profile("!local-pg")
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        // SimpleVectorStore.builder() is the preferred factory in Spring AI 1.1.x.
        // It wraps the model and sets up the cosine-similarity search engine.
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * Local ONNX embedding model — active only in the 'local-pg' profile.
     *
     * Uses all-MiniLM-L6-v2 (sentence-transformers) via ONNX Runtime.
     * Produces 384-dim vectors locally — no external API required.
     * The ~91MB ONNX model file is downloaded from HuggingFace on first startup
     * and cached in ~/.cache/spring-ai/ for subsequent runs.
     *
     * @Primary ensures this bean wins over the auto-configured OpenAiEmbeddingModel
     * (which is still on the classpath from spring-ai-starter-model-openai used for chat).
     * Both beans exist in the context; @Primary tells Spring which one to inject
     * into PgVectorStore and other EmbeddingModel consumers.
     *
     * MERN/Next.js analogy (transformers.js):
     *   const extractor = await pipeline('feature-extraction', 'Xenova/all-MiniLM-L6-v2')
     *   const output = await extractor('some text', { pooling: 'mean', normalize: true })
     *
     * Book ref: Chapter 18 — RAG: Embedding & Indexing
     *   "Local embedding models eliminate API latency and quota limits.
     *    Trade-off: larger binary, first-run model download, CPU-only speed."
     */
    @Bean
    @Primary
    @Profile("local-pg")
    public EmbeddingModel localEmbeddingModel() {
        // Default model: sentence-transformers/all-MiniLM-L6-v2 (384 dims)
        // Spring calls afterPropertiesSet() automatically (InitializingBean)
        return new TransformersEmbeddingModel();
    }

    /**
     * PostgreSQL vector store — active only in the 'local-pg' profile.
     *
     * PgVectorStore stores document embeddings in a `vector` column and performs
     * approximate nearest-neighbour (ANN) search via the pgvector extension.
     * The HNSW index (created by Flyway V4) enables sub-linear search time.
     *
     * initializeSchema(false): Flyway owns the schema — V4__vector_store.sql
     * already creates the table and HNSW index.  Setting this to true would
     * cause PgVectorStore to attempt CREATE TABLE on every startup (redundant
     * and potentially conflicting with the Flyway-managed schema).
     *
     * dimensions must match the embedding model output:
     *   • Gemini text-embedding-004 → 768 dims
     *   • all-MiniLM-L6-v2 (ONNX)  → 384 dims
     *   • text-embedding-ada-002    → 1536 dims
     *
     * MERN analogy (Supabase vector store):
     *   const vectorStore = await SupabaseVectorStore.fromDocuments([], embeddings, {
     *     client: supabase, tableName: 'vector_store', queryName: 'match_documents'
     *   })
     *
     * Book ref: Chapter 18 — RAG: Embedding & Indexing
     *   "Switch to pgvector for persistence + ANN indexing (HNSW or ivfflat)
     *    once you outgrow an in-memory store."
     *
     * Book ref: Chapter 19 — RAG: Retrieval & Reranking
     *   "PgVector with HNSW index provides O(log n) ANN search, dramatically
     *    faster than SimpleVectorStore's O(n) brute-force scan."
     */
    @Bean
    @Profile("local-pg")
    public VectorStore pgVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            @Value("${app.vector-store.dimensions:384}") int dimensions) {

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                // Dimension must match the embedding model (384 for all-MiniLM-L6-v2 ONNX)
                .dimensions(dimensions)
                // Use cosine distance — standard for text embeddings
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                // Flyway V4 owns the schema — do NOT let PgVectorStore recreate the table
                .initializeSchema(false)
                .build();
    }
}
