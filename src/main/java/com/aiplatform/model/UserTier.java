package com.aiplatform.model;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * UserTier — user subscription tier controlling routing + model selection
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Platform-level billing concept: every SaaS product built on ai-platform-core
 * will have billing tiers that control agent routing and model selection.
 *
 * MERN/Next.js analogy (Mastra dynamic routing):
 *   type UserTier = 'FREE' | 'PREMIUM'
 *   const route = tier === 'PREMIUM' && isLong ? 'fullPipeline' : 'singleStep'
 *
 * Storage:
 *   Stored as VARCHAR in user_profile.tier (Flyway V3 migration).
 *
 * Model mapping (from application.yml):
 *   FREE    → app.agent.model.free    (gemini-2.0-flash  — faster, cheaper)
 *   PREMIUM → app.agent.model.premium (gemini-1.5-pro    — more capable)
 *
 * Book ref: Chapter 8 — Dynamic Agents
 *   "Dynamic agents change their instructions, tools, or model at runtime
 *    based on context.  User tier is the simplest form of context."
 * ══════════════════════════════════════════════════════════════════════════
 */
public enum UserTier {

    FREE,

    PREMIUM;

    /**
     * Fail-safe tier parser — unknown/null values default to FREE.
     *
     * MERN analogy: z.enum(['FREE','PREMIUM']).catch('FREE').parse(raw)
     *
     * @param raw the raw string from an HTTP header (may be null)
     * @return the matching UserTier, or FREE if unrecognised
     */
    public static UserTier parse(String raw) {
        if (raw == null) return FREE;
        try {
            return UserTier.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FREE;
        }
    }
}
