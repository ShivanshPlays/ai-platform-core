package com.aiplatform.guardrail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * RateLimiter — Sliding-window rate limit per userId
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Enforces a maximum number of requests per user per 60-second rolling window.
 * Backed by an in-memory ConcurrentHashMap (resets on restart).
 * Phase 9 (Observability) can make the window configurable per user tier.
 *
 * Algorithm: sliding-window counter
 *   - Per userId, maintain a queue of timestamps of recent requests.
 *   - On each new request: remove timestamps older than (now - windowMs).
 *   - If queue size >= limit, throw RateLimitExceededException.
 *   - Otherwise, add now to the queue and allow the request.
 *
 * Why sliding window over fixed window?
 *   Fixed window (reset every 60s) can allow 2× the limit at boundary:
 *     59 requests at second 59, then 59 more at second 61 = 118 in ~2s.
 *   Sliding window prevents this by always looking at the last 60 seconds.
 *
 * MERN/Next.js analogy:
 *   Equivalent of the express-rate-limit sliding-window store in Node:
 *     import rateLimit from 'express-rate-limit'
 *     app.use(rateLimit({ windowMs: 60_000, max: 10, keyGenerator: req => req.userId }))
 *
 * Note:
 *   This is an in-memory store — not suitable for multi-instance deployments.
 *   Phase 9 or a future Redis integration would replace the map with a shared
 *   counter.  For Phase 6 (single-instance dev), this is sufficient.
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   Rate limiting protects against two threats:
 *     1. Abuse: a malicious user sending thousands of requests per minute.
 *     2. Cost runaway: each LLM call has a financial cost; unbounded calls
 *        could exhaust a budget before you notice.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Component
public class RateLimiter {

    // Rolling window: 60 seconds in milliseconds
    private static final long WINDOW_MS = 60_000L;

    // userId → queue of request timestamps within the current window
    // ConcurrentLinkedQueue is lock-free for reads; putIfAbsent is atomic.
    // MERN analogy: const store = new Map<string, number[]>()
    private final Map<String, Queue<Long>> windows = new ConcurrentHashMap<>();

    private final int maxRequestsPerMinute;

    public RateLimiter(
            @Value("${app.guardrail.rate-limit.requests-per-minute:10}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    /**
     * Record one request from {@code userId} and enforce the rate limit.
     *
     * @param userId the identifier for the requesting user (header, IP, or default)
     * @throws RateLimitExceededException if the user has exceeded their quota
     *
     * MERN analogy:
     *   function checkRateLimit(userId: string): void {
     *     const now = Date.now()
     *     const hits = store.get(userId) ?? []
     *     const recent = hits.filter(t => t > now - 60_000)
     *     if (recent.length >= MAX) throw new TooManyRequestsError()
     *     store.set(userId, [...recent, now])
     *   }
     */
    public void check(String userId) {
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        // Get or create the queue for this user.
        Queue<Long> hits = windows.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>());

        // Evict timestamps outside the current window.
        hits.removeIf(timestamp -> timestamp < windowStart);

        if (hits.size() >= maxRequestsPerMinute) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded for userId=" + userId +
                    "; max " + maxRequestsPerMinute + " requests/minute.");
        }

        hits.add(now);
    }
}
