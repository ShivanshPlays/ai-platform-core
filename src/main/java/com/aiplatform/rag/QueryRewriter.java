package com.aiplatform.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 12.5 — QueryRewriter (RAG level 4)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Responsibility: given a user's nutrition query, generate 2 alternative
 * phrasings to improve RAG recall.  The expanded query set is used in
 * ResearchAgent.gatherFacts() to run multiple retrieval passes, then the
 * combined results are reranked before injection into the prompt.
 *
 * WHY QUERY REWRITING?
 *   A single query may miss relevant documents because:
 *   • The user used different terminology than the document author
 *     (e.g. "omega-3 brain health" vs "DHA cognitive function")
 *   • The query is ambiguous or too general
 *   • The embedding model under-represents certain phrasings
 *
 *   Generating 2-3 query variants and retrieving for each (then deduplicating
 *   and reranking) significantly improves recall at the same precision level.
 *   This is called "Multi-Query Retrieval" or "HyDE" (Hypothetical Document
 *   Embeddings) depending on the variant used.
 *
 *   Here we use a simple prompt-based rewrite strategy (not HyDE):
 *   the LLM is asked to rephrase the question from different angles.
 *
 * WHAT CHANGES IN THE PIPELINE:
 *   Before (Phase 10):
 *     topic → retrieveContext(topic) → 1 retrieval pass
 *
 *   After (Phase 12.5):
 *     topic
 *       └─► QueryRewriter.rewrite(topic)
 *             → [original, variant1, variant2]
 *       └─► retrieveContext(original)
 *       └─► retrieveContext(variant1)
 *       └─► retrieveContext(variant2)
 *       └─► combine → RerankingService.rerank() → top-K context
 *
 * noOp() factory:
 *   Used by ResearchAgent's no-arg constructor (test-friendly) — returns a
 *   QueryRewriter that does NOT call the LLM; it simply passes the original
 *   query through unchanged.
 *   MERN analogy: jest.fn().mockResolvedValue([originalQuery])
 *
 * MERN/Next.js analogy:
 *   Like this LangChain.js pattern:
 *     const rewriter = new MultiQueryRetriever.fromLLM({
 *       llm: model, retriever: vectorStore.asRetriever(),
 *       queryCount: 3
 *     })
 *   Or the Mastra equivalent:
 *     const variants = await generateObject({
 *       model, schema: z.object({ variants: z.array(z.string()) }),
 *       prompt: `Generate 2 alternative phrasings of: "${query}"`
 *     })
 *
 * Book ref: Chapter 19 — RAG: Retrieval & Reranking
 *   "Query rewriting is one of the highest-leverage RAG improvements: it costs
 *    one extra LLM call but can double retrieval recall by covering multiple
 *    phrasings of the same intent."
 *
 * Book ref: Chapter 19 — RAG: Retrieval & Reranking
 *   "Multi-Query Retrieval: run the same retrieval for N query variants,
 *    union the results, and rerank.  Works well for short, ambiguous queries."
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Component
public class QueryRewriter {

    // Maximum number of variants to generate (not including the original).
    // Keeping this small (2) limits the extra LLM cost per research request.
    private static final int MAX_VARIANTS = 2;

    private final ChatClient chatClient;

    /**
     * Spring-managed constructor.
     * Injects the ChatClient bean from ChatClientConfig directly — that bean
     * is explicitly configured to point at Google AI Studio's OpenAI-compat
     * endpoint with the Gemini API key.
     * ChatClient.Builder was previously used here but it autowires to
     * whichever ChatModel Spring resolves first, which could be the competing
     * auto-configured OpenAiChatModel pointing at api.openai.com.
     * @Autowired needed because this class has multiple constructors.
     *
     * MERN analogy: constructor-injected ChatClient = importing the AI SDK
     *   instance at the top of a Next.js service file.
     */
    @Autowired
    public QueryRewriter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    // Package-private for noOp factory
    QueryRewriter() {
        this.chatClient = null;
    }

    /**
     * Structured output record for query variant generation.
     *
     * Spring AI's .call().entity() deserialises the LLM's JSON response
     * into this record automatically — no manual parsing needed.
     *
     * MERN analogy:
     *   const schema = z.object({ variants: z.array(z.string()).max(2) })
     *   const { object } = await generateObject({ model, schema, prompt })
     */
    record QueryVariants(List<String> variants) {}

    /**
     * Generate up to MAX_VARIANTS alternative phrasings of the query,
     * then return the original + variants as a deduplicated list.
     *
     * Always includes the original query as the first element so retrieval
     * is never worse than the Phase 10 single-query approach.
     *
     * @param query the original nutrition topic / question
     * @return [original] + up to MAX_VARIANTS alternative phrasings
     *
     * MERN analogy:
     *   async function rewrite(query: string): Promise<string[]> {
     *     const { object } = await generateObject({ model, schema, prompt })
     *     return [query, ...object.variants].filter(unique)
     *   }
     *
     * Book ref: Chapter 19 — RAG: Retrieval & Reranking
     *   "Include the original query in the retrieval set: the rewritten
     *    variants complement rather than replace the original intent."
     */
    public List<String> rewrite(String query) {
        if (chatClient == null || query == null || query.isBlank()) {
            return List.of(query != null ? query : "");
        }

        try {
            QueryVariants variants = chatClient.prompt()
                    .system("""
                            You are a search query optimisation assistant.
                            Given a nutrition research query, generate exactly %d alternative phrasings
                            that use different vocabulary but capture the same intent.
                            Focus on: synonyms, scientific vs colloquial terms, specificity variations.
                            Return ONLY valid JSON: {"variants": ["...", "..."]}
                            No markdown fences. No explanation. JSON only.
                            """.formatted(MAX_VARIANTS))
                    .user(query)
                    .call()
                    .entity(QueryVariants.class);

            // Build the combined list: original always first, then valid variants
            List<String> all = new ArrayList<>();
            all.add(query);
            if (variants != null && variants.variants() != null) {
                variants.variants().stream()
                        .filter(v -> v != null && !v.isBlank() && !v.equals(query))
                        .limit(MAX_VARIANTS)
                        .forEach(all::add);
            }
            return List.copyOf(all);

        } catch (Exception e) {
            // If the LLM call fails for any reason, fall back to the original query.
            // This ensures the RAG pipeline is never blocked by a rewrite failure.
            // MERN analogy: catch + return [query] as fallback
            return List.of(query);
        }
    }

    /**
     * No-op factory — returns a QueryRewriter that passes the query through
     * unchanged without calling the LLM.  Used by ResearchAgent's no-arg
     * constructor so existing unit tests don't need a ChatClient bean.
     *
     * MERN analogy: jest.fn().mockResolvedValue([query])  — identity stub.
     *
     * Book ref: Chapter 27 — Evaluations Overview
     *   "Stubs that return the input unchanged are the simplest valid test
     *    double: they verify that the pipeline handles 'no enrichment'
     *    gracefully."
     */
    public static QueryRewriter noOp() {
        return new QueryRewriter() {
            @Override
            public List<String> rewrite(String query) {
                return query != null ? List.of(query) : List.of();
            }
        };
    }
}
