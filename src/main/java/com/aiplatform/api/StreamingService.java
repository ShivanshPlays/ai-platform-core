package com.aiplatform.api;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * StreamingService — shared adaptor wrapping ChatClient.stream()
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * This is the single shared streaming abstraction for all Phase 12 endpoints.
 * One method, one responsibility: wrap ChatClient's streaming API as a
 * typed Flux<String> of token chunks.
 *
 * Why a separate service (not inline in each controller)?
 *   Three controllers need streaming: chat, coach-advice, plan.
 *   Centralising the ChatClient wiring here means:
 *     • No per-endpoint ChatClient plumbing
 *     • One place to change if the streaming API changes (e.g. add retry)
 *     • Easy to mock in tests: inject a stub StreamingService
 *   MERN analogy: exporting a shared `streamText` factory from `lib/ai.ts`
 *   that every API route imports instead of each one creating its own client.
 *
 * MERN/Next.js analogy (Vercel AI SDK):
 *   // lib/ai.ts
 *   export function stream(systemPrompt, userMessage) {
 *     return streamText({
 *       model: google('gemini-2.5-flash'),
 *       system: systemPrompt,
 *       prompt: userMessage,
 *     }).textStream   // AsyncIterable<string>
 *   }
 *
 * Spring AI streaming API:
 *   chatClient.prompt()
 *     .system(systemPrompt)  // ← same as `system` in generateText()
 *     .user(userMessage)     // ← same as `prompt` in generateText()
 *     .stream()              // ← returns StreamResponseSpec (not BlockingResponseSpec)
 *     .content()             // ← Flux<String> — each emission is one token chunk
 *
 * Book ref: Chapter 12 — Streaming
 *   "The streaming API returns tokens incrementally as the model generates them.
 *    A shared adaptor layer centralises provider wiring and makes swapping
 *    the underlying model a single-class change."
 *
 * Book ref: Chapter 2 — Choosing a Provider & Model
 *   The ChatClient hides the provider (Gemini via OpenAI-compat endpoint).
 *   Switching to a different model is a configuration change only.
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Service
public class StreamingService {

    // MERN analogy: the OpenAI client instance (created once, reused everywhere)
    // Spring injects the auto-configured ChatClient bean from ChatClientConfig.
    private final ChatClient chatClient;

    public StreamingService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Stream an LLM response as a sequence of token chunks.
     *
     * MERN/Next.js analogy (Vercel AI SDK):
     *   const result = await streamText({ system: systemPrompt, prompt: userMessage })
     *   result.textStream   // AsyncIterable<string> — each iteration yields one chunk
     *
     * Spring AI equivalent:
     *   chatClient.prompt().system(s).user(u).stream().content()
     *   // returns Flux<String> — reactive stream of token chunks
     *
     * @param systemPrompt the system context (role + data + instructions)
     * @param userMessage  the user query passed as the human turn
     * @return Flux<String> emitting one string per token chunk as they arrive
     *
     * Book ref: Chapter 12 — Streaming
     *   "The streaming API lets you pipe tokens to the client as they arrive.
     *    Use Flux<String> with produces=text/event-stream in Spring MVC to
     *    send each token as a Server-Sent Event (SSE) data: line."
     */
    public Flux<String> stream(String systemPrompt, String userMessage) {
        // Spring AI streaming call — equivalent of Vercel AI SDK streamText()
        // .stream() returns StreamResponseSpec; .content() returns Flux<String>
        // MERN analogy: (await streamText({ system, prompt })).textStream
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .stream()
                .content();  // Flux<String> — one emission per token chunk
    }
}
