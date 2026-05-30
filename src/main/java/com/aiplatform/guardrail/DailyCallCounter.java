package com.aiplatform.guardrail;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory daily-window counter for rate limiting by arbitrary key (IP, userId, etc.).
 * Key format: "identifier|ruleKey" → count today.
 * Resets automatically when the date changes (lazy eviction on access).
 *
 * Use case: feed daily counts into a rule engine (Drools) for tier-based daily caps,
 * separate from the per-minute burst protection in {@link RateLimiter}.
 *
 * For multi-instance deployments, replace with Redis INCR + TTL.
 */
@Component
public class DailyCallCounter {

    private volatile LocalDate currentDay = LocalDate.now();
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    /**
     * Records a hit and returns the count for today (inclusive of this hit).
     */
    public int recordAndGetCount(String identifier, String ruleKey) {
        evictIfNewDay();
        String key = identifier + "|" + ruleKey;
        AtomicInteger counter = counts.computeIfAbsent(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet();
    }

    /**
     * Returns current count for today WITHOUT recording a new hit.
     */
    public int getCount(String identifier, String ruleKey) {
        evictIfNewDay();
        String key = identifier + "|" + ruleKey;
        AtomicInteger counter = counts.get(key);
        return counter != null ? counter.get() : 0;
    }

    private void evictIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay)) {
            counts.clear();
            currentDay = today;
        }
    }
}
