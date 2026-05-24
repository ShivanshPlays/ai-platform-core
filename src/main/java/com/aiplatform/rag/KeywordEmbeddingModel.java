package com.aiplatform.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 10 — Bag-of-words keyword embedding model (dev / learning stub)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * WHY NOT call a real embedding API?
 *   Phase 10 starts at "plain-text storage with keyword search" (complexity
 *   level 1 in the RAG complexity ladder in plan.md).  A real embedding API
 *   (Google text-embedding-004, OpenAI text-embedding-3-small) would require:
 *     • Extra endpoint config for the Google AI Studio OpenAI-compat URL
 *     • API quota during unit tests
 *     • Network access from CI
 *
 *   A bag-of-words model avoids all that AND still teaches the same RAG
 *   architecture concepts: chunk → embed → store → retrieve → inject.
 *   Phase 10 "level 2" (plan.md) replaces this with pgvector + real embeddings.
 *
 * HOW IT WORKS — bag-of-words vector:
 *   1. Normalise text: lowercase, strip punctuation
 *   2. Tokenise into words
 *   3. For each word, compute abs(hashCode) % DIMENSIONS → that index gets +1
 *   4. L2-normalise the vector so cosine similarity works correctly
 *
 *   Documents about the same topic share vocabulary, so they hash to similar
 *   indices → similar vectors → high cosine similarity.  Not semantic, but
 *   nutritionally coherent (all omega-3 docs share "epa", "dha", "fish" etc.).
 *
 * SWITCHING TO REAL EMBEDDINGS (Phase 10, level 2):
 *   Replace this class with proper endpoint config in application.yml:
 *
 *     spring.ai.openai.embedding.base-url: https://generativelanguage.googleapis.com/v1beta/openai
 *     spring.ai.openai.embedding.api-key: ${GEMINI_API_KEY}
 *     spring.ai.openai.embedding.options.model: text-embedding-004
 *
 *   Then delete this @Component class and remove @Primary — the OpenAI
 *   auto-configured EmbeddingModel will take over automatically.
 *
 * @Primary annotation:
 *   spring-ai-starter-model-openai auto-configures OpenAiEmbeddingModel.
 *   @Primary ensures VectorStoreConfig uses THIS model when multiple
 *   EmbeddingModel beans are in the context.
 *   MERN analogy: module.exports = { default: keywordModel } overriding
 *   a peer dependency's default export.
 *
 * Book ref: Chapter 18 — RAG: Embedding & Indexing
 *   "The embedding model converts text to a fixed-length vector. The choice
 *    of model determines what 'similar' means: semantic similarity, keyword
 *    overlap, or domain-specific closeness."
 * ═══════════════════════════════════════════════════════════════════════════
 */
// Active only when NOT using the local-pg profile.
// In the local-pg profile this bean is absent, allowing Spring AI's
// auto-configured OpenAiEmbeddingModel (text-embedding-004) to take over.
// MERN analogy: conditional export — export default process.env.USE_PG ? realEmbed : keywordEmbed
//
// Book ref: Chapter 18 — RAG: Embedding & Indexing
//   "Swap the embedding model by swapping the bean — the rest of the
//    pipeline (chunking, storage, retrieval) stays unchanged."
@Profile("!local-pg")
@Primary
@Component
public class KeywordEmbeddingModel implements EmbeddingModel {

    // 384 dimensions matches the lightweight all-MiniLM-L6-v2 convention.
    // When switching to text-embedding-004, update to 768; for ada-002 use 1536.
    public static final int DIMENSIONS = 384;

    /**
     * Core method: embed a batch of texts.  All default EmbeddingModel methods
     * (embed(String), embed(List<String>)) delegate here.
     *
     * MERN analogy: the `embed()` function in Vercel AI SDK:
     *   const { embedding } = await embed({ model, value: text })
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            float[] vector = computeVector(instructions.get(i));
            embeddings.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    /**
     * Embed a Document — uses getText() which is the raw text field in Spring AI 1.1.x.
     * This method is abstract in EmbeddingModel (unlike embed(String) which has a default).
     */
    @Override
    public float[] embed(Document document) {
        return computeVector(document.getText());
    }

    /**
     * Compute a bag-of-words float[384] vector for a text string.
     * Package-visible so tests can verify determinism and collision-freedom.
     *
     * Algorithm:
     *   1. Lowercase + strip punctuation
     *   2. Tokenise on whitespace
     *   3. Map each token to an index: abs(hashCode) % 384, increment by 1
     *   4. L2-normalise so cosine-similarity is bounded to [-1, 1]
     */
    static public float[] computeVector(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) return vector;

        // Step 1-2: normalise and tokenise
        String normalised = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        StringTokenizer tokenizer = new StringTokenizer(normalised);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            // Step 3: deterministic index from token hash (abs guards against Integer.MIN_VALUE)
            int idx = (token.hashCode() & Integer.MAX_VALUE) % DIMENSIONS;
            vector[idx] += 1.0f;
        }

        // Step 4: L2-normalise (unit-length vectors → cosine similarity = dot product)
        float norm = 0f;
        for (float v : vector) norm += v * v;
        if (norm > 0f) {
            norm = (float) Math.sqrt(norm);
            for (int i = 0; i < DIMENSIONS; i++) vector[i] /= norm;
        }
        return vector;
    }

    // ── [DO-NOT-REMOVE: COSINE-DRY-RUN] ──────────────────────────────────────
    // MATHEMATICAL DRY RUN — Cosine similarity / dot product for query matching
    // ─────────────────────────────────────────────────────────────────────────
    //
    // At retrieval time, SimpleVectorStore embeds the query text the SAME way
    // computeVector() does, then scores EVERY stored chunk against it.
    // Because all vectors are L2-normalised (unit length = 1), cosine similarity
    // reduces to a plain dot product:
    //
    //   cosine_similarity(A, B) = (A · B) / (|A| * |B|)
    //                           = (A · B) / (1 * 1)      ← because L2-norm = 1
    //                           = A · B
    //
    // ── CONCRETE EXAMPLE (simplified to 5 dimensions for readability) ──
    //
    // Stored document: "Fish oil is rich in omega-3"
    //   Tokens: fish, oil, is, rich, in, omega, 3
    //   Raw counts at indices (example hash results):
    //     idx 0 → fish  = 1.0
    //     idx 1 → oil   = 1.0
    //     idx 2 → is    = 1.0
    //     idx 3 → rich  = 1.0
    //     idx 4 → omega = 1.0
    //   norm = sqrt(1²+1²+1²+1²+1²) = sqrt(5) ≈ 2.236
    //   A (normalised) = [0.447, 0.447, 0.447, 0.447, 0.447]
    //
    // Query: "omega-3 fish benefits"
    //   Tokens: omega, 3, fish, benefits
    //   Raw counts (assuming "3" and "benefits" hash to unused indices):
    //     idx 0 → fish  = 1.0
    //     idx 4 → omega = 1.0
    //     (others land at indices not present in doc vector → contribute 0)
    //   norm = sqrt(1²+1²) = sqrt(2) ≈ 1.414
    //   B (normalised) = [0.707, 0.0, 0.0, 0.0, 0.707]
    //
    // Dot product (= cosine similarity since both are unit vectors):
    //   A · B = (0.447 * 0.707) + (0.447 * 0.0) + (0.447 * 0.0)
    //         + (0.447 * 0.0)   + (0.447 * 0.707)
    //         = 0.316 + 0 + 0 + 0 + 0.316
    //         = 0.632
    //
    // A score of 0.632 (out of max 1.0) indicates HIGH similarity — the doc
    // shares 2 of the 4 query keywords ("fish" + "omega").
    //
    // An unrelated document like "Vitamin C boosts immunity":
    //   Tokens: vitamin, c, boosts, immunity  → all hash to different indices
    //   B_unrelated · A ≈ 0.0  (no shared vocabulary → dot product ≈ 0)
    //
    // SimpleVectorStore ranks chunks by this score and returns the top-K.
    // That top-K list is then injected into the LLM prompt as grounding context.
    //
    // MERN/Next.js analogy:
    //   // LangChain.js equivalent:
    //   const results = await vectorStore.similaritySearch(query, k=3)
    //   // results[0].pageContent → highest scoring chunk text
    //
    // Book ref: Chapter 19 — RAG: Retrieval & Ranking
    //   "Cosine similarity on unit-normalised vectors is equivalent to a dot
    //    product and is the standard retrieval metric for dense vector search."
    // ── [END DO-NOT-REMOVE: COSINE-DRY-RUN] ──────────────────────────────────
}
// When a document is ingested, its text is converted to this vector.
// The vector is stored in the vector store alongside the chunk text.
// At retrieval time, a query is embedded the same way, and cosine similarity (dot product, since vectors are normalized) is used to find the most similar stored chunks.
// This enables keyword-based similarity search for RAG, even with this simple embedding.