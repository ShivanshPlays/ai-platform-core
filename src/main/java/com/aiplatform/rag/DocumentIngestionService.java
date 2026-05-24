package com.aiplatform.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 10 — DocumentIngestionService
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Responsibility: accept plain-text documents, split them into chunks,
 * embed each chunk, and persist the embeddings in the VectorStore.
 *
 * PIPELINE (RAG complexity level 1):
 *   raw text
 *     ↓ TokenTextSplitter — splits on whitespace/sentences, max ~800 tokens
 *   List<Chunk>
 *     ↓ VectorStore.add() — embeds via KeywordEmbeddingModel, stores in memory
 *   indexed embeddings (ready for similarity search)
 *
 * MERN analogy (LangChain.js):
 *   const splitter = new RecursiveCharacterTextSplitter({ chunkSize: 800 })
 *   const chunks = await splitter.splitText(rawText)
 *   await vectorStore.addDocuments(chunks.map(c => new Document(c)))
 *
 * Why chunk before embedding?
 *   • LLMs have limited context windows; embedding the whole document as one
 *     unit loses granularity at retrieval time.
 *   • At query time, you retrieve only the relevant chunk, not the whole doc.
 *   • Smaller chunks have more precise embeddings → higher retrieval precision.
 *
 * Book ref: Chapter 17 — RAG: Chunking & Ingestion
 *   "Chunk size is the single most important hyperparameter in RAG.
 *    Too large → imprecise retrieval.  Too small → lost context."
 *
 * Book ref: Chapter 18 — RAG: Embedding & Indexing
 *   "Store the embedding alongside the source text and document metadata so
 *    the retriever can return grounded text, not just similarity scores."
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Service
public class DocumentIngestionService {

    // ── Default chunking config ────────────────────────────────────────────
    // 800 tokens ≈ 600-700 words — fits well within a prompt section.
    // In Phase 10 level 2 (pgvector), make this configurable via @Value.
    private static final int DEFAULT_CHUNK_SIZE    = 800;
    private static final int DEFAULT_MIN_CHUNK     = 350;
    private static final int DEFAULT_MAX_CHUNKS    = 10_000;

    // VectorStore interface — backed by SimpleVectorStore (H2 profile) or
    // PgVectorStore (local-pg profile). Injecting the interface keeps this
    // service decoupled from the backing implementation.
    // MERN analogy: coding to the VectorStore interface, not the Pinecone or
    //   MemoryVectorStore class — swap backing store without touching this service.
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    // MERN analogy: constructor injection = importing services at the top of
    // a Next.js route handler and passing them as function arguments.
    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        // TokenTextSplitter is Spring AI's built-in chunker.
        // It respects sentence boundaries and falls back to token counts.
        // MERN analogy: LangChain.js RecursiveCharacterTextSplitter
        this.splitter = new TokenTextSplitter(
                DEFAULT_CHUNK_SIZE,     // chunkSize
                DEFAULT_MIN_CHUNK,      // minChunkSizeChars
                5,                      // minChunkLengthToEmbed
                DEFAULT_MAX_CHUNKS,     // maxNumChunks
                true                    // keepSeparator
        );
    }

    /**
     * Ingest plain-text content: split → embed → store.
     *
     * @param docName  logical document name used as metadata (for filtering)
     * @param content  raw UTF-8 text (nutrition guide, research paper excerpt, etc.)
     * @return IngestResult with doc name + number of chunks indexed
     *
     * MERN analogy:
     *   export async function ingestDocument(docName: string, content: string) {
     *     const chunks = await splitter.splitText(content)
     *     await vectorStore.addDocuments(chunks.map(c => ({ pageContent: c, metadata: { docName } })))
     *     return { docName, chunkCount: chunks.length }
     *   }
     *
     * Book ref: Chapter 17 — RAG: Chunking & Ingestion
     *   "Each chunk should be self-contained enough to make sense without
     *    surrounding text — this makes retrieval outputs coherent."
     */
    public IngestResult ingestText(String docName, String content) {
        // Step 1: wrap raw text in a Spring AI Document with metadata
        Document rawDoc = new Document(content, Map.of("docName", docName));

        // Step 2: split into chunks — TokenTextSplitter returns a List<Document>
        // Each chunk inherits the parent metadata (docName) automatically.
        // MERN analogy: const chunks = await splitter.createDocuments([content])
        List<Document> chunks = splitter.apply(List.of(rawDoc));

        // Guard: if splitter returns nothing (content too short), index as-is
        List<Document> toStore = chunks.isEmpty() ? List.of(rawDoc) : chunks;

        // Step 3: embed + persist  — VectorStore.add() calls EmbeddingModel internally:
        //   for each chunk → embeddingModel.embed(chunk.content) → store (text, vector)
        // MERN analogy: await vectorStore.addDocuments(toStore)
        vectorStore.add(toStore);

        return new IngestResult(docName, toStore.size());
    }

    /**
     * Ingest multiple documents in a single call.
     * Returns the total number of chunks indexed across all documents.
     *
     * MERN analogy: Promise.all(docs.map(d => ingestDocument(d.name, d.content)))
     */
    public List<IngestResult> ingestAll(List<RawDocument> rawDocuments) {
        List<IngestResult> results = new ArrayList<>();
        for (RawDocument rd : rawDocuments) {
            results.add(ingestText(rd.docName(), rd.content()));
        }
        return results;
    }

    // ── Domain records (scoped to the rag package) ─────────────────────────

    /**
     * Input model for batch ingestion.
     * MERN analogy: type RawDocument = { docName: string; content: string }
     */
    public record RawDocument(String docName, String content) {}

    /**
     * Result returned to the caller after ingestion.
     * MERN analogy: type IngestResult = { docName: string; chunkCount: number }
     *
     * Book ref: Chapter 17 — RAG: Chunking & Ingestion
     *   "Return the chunk count so the caller can sanity-check that chunking
     *    worked as expected — especially important when testing in dev."
     */
    public record IngestResult(String docName, int chunkCount) {}
}
