package com.aiplatform.guardrail;

/**
 * Thrown when the rate limiter detects too many requests from a single userId
 * within the configured time window.
 *
 * MERN analogy: a 429 Too Many Requests error thrown from an Express
 * rate-limiting middleware (e.g. express-rate-limit).
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   Rate limiting protects against abuse and runaway LLM costs.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
