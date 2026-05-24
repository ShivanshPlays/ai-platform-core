package com.aiplatform.memory;

import com.aiplatform.model.UserTier;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * BaseUserProfile — Platform-level user profile base class
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Contains only platform-level fields shared by all domain products:
 *   - userId (PK)
 *   - displayName
 *   - tier (billing/routing)
 *   - updatedAt
 *
 * Domain products extend this with their own fields (e.g. dietaryGoals
 * for NutritionCoach, skills/techStack for CareerCopilot).
 *
 * MERN/Next.js analogy:
 *   // Base interface in packages/core/types.ts:
 *   interface BaseUserProfile {
 *     userId: string
 *     displayName?: string
 *     tier: 'FREE' | 'PREMIUM'
 *     updatedAt: Date
 *   }
 *
 * Book ref: Chapter 7 — Memory
 *   "Semantic memory = stable facts about the user (name, goals, preferences).
 *    Retrieved once per session and injected into the system prompt."
 * ══════════════════════════════════════════════════════════════════════════
 */
@MappedSuperclass
public abstract class BaseUserProfile {

    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private UserTier tier = UserTier.FREE;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    protected BaseUserProfile() {}

    protected BaseUserProfile(String userId) {
        this.userId = userId;
    }

    // Fluent setters
    @SuppressWarnings("unchecked")
    public <T extends BaseUserProfile> T displayName(String name) {
        this.displayName = name;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseUserProfile> T tier(UserTier t) {
        this.tier = t;
        return (T) this;
    }

    public String getUserId()       { return userId; }
    public String getDisplayName()  { return displayName; }
    public UserTier getTier()       { return tier; }
    public Instant getUpdatedAt()   { return updatedAt; }
}
