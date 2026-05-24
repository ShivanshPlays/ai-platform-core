package com.aiplatform.guardrail;
import com.aiplatform.guardrail.RateLimiter;
import com.aiplatform.guardrail.RateLimitExceededException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimiter — sliding-window in-memory rate limit.
 *
 * No Spring context needed; plain Java test.
 *
 * MERN/Next.js analogy:
 *   Equivalent of a Jest test for express-rate-limit:
 *     it('allows first N requests', () => { ... })
 *     it('blocks the N+1th request', () => { ... })
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   Rate limiting protects against cost runaway and abuse; the sliding
 *   window prevents boundary bursts that a fixed window allows.
 */
class RateLimiterTest {

    // Use a limit of 3 requests/min so tests run without many iterations.
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(3);
    }

    // ── Happy path ─────────────────────────────────────────────────────────

    @Test
    void firstRequestIsAllowed() {
        assertDoesNotThrow(() -> rateLimiter.check("user1"),
                "The very first request should pass");
    }

    @Test
    void requestsUpToLimitAreAllowed() {
        // 3 requests should all be allowed (limit = 3)
        assertDoesNotThrow(() -> {
            rateLimiter.check("user-a");
            rateLimiter.check("user-a");
            rateLimiter.check("user-a");
        }, "Exactly max requests should all pass");
    }

    // ── Rate limit enforcement ─────────────────────────────────────────────

    @Test
    void requestExceedingLimitIsBlocked() {
        // Fill up the limit
        rateLimiter.check("user-b");
        rateLimiter.check("user-b");
        rateLimiter.check("user-b");

        // 4th request must be blocked
        assertThrows(RateLimitExceededException.class,
                () -> rateLimiter.check("user-b"),
                "The request exceeding the limit should throw RateLimitExceededException");
    }

    @Test
    void exceptionMessageContainsUserId() {
        rateLimiter.check("user-xyz");
        rateLimiter.check("user-xyz");
        rateLimiter.check("user-xyz");

        var ex = assertThrows(RateLimitExceededException.class,
                () -> rateLimiter.check("user-xyz"));
        assertTrue(ex.getMessage().contains("user-xyz"),
                "Exception message should identify the limited userId");
    }

    // ── User isolation ─────────────────────────────────────────────────────

    @Test
    void differentUsersHaveIndependentWindows() {
        // user-c uses up all 3 slots
        rateLimiter.check("user-c");
        rateLimiter.check("user-c");
        rateLimiter.check("user-c");

        // user-d should still be allowed
        assertDoesNotThrow(() -> rateLimiter.check("user-d"),
                "A different userId must not be affected by another user's rate limit");
    }

    @Test
    void multipleUsersConcurrentlyAllowed() {
        // Interleaved requests from two users — each under the limit
        assertDoesNotThrow(() -> {
            rateLimiter.check("alice");
            rateLimiter.check("bob");
            rateLimiter.check("alice");
            rateLimiter.check("bob");
            rateLimiter.check("alice"); // alice at limit
            rateLimiter.check("bob");  // bob at limit
        });
    }
}
