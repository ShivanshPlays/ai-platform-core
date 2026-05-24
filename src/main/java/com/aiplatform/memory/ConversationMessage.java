package com.aiplatform.memory;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * ConversationMessage — JPA entity for short-term / session memory
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Represents one turn in a conversation (user message OR assistant reply).
 * The window of last N messages is injected into the LLM prompt to give
 * the model conversational context.
 *
 * MERN/Next.js analogy:
 *   Equivalent to a Prisma model:
 *     model ConversationMessage {
 *       id        Int      @id @default(autoincrement())
 *       userId    String
 *       role      String   // 'user' | 'assistant' | 'tool'
 *       content   String
 *       createdAt DateTime @default(now())
 *     }
 *   Or a Mongoose schema with { userId, role, content, createdAt }.
 *
 * @Entity (JPA) vs @Document (MongoDB):
 *   @Entity tells Hibernate this class maps to a relational table row.
 *   @Document would be the MongoDB/Spring Data equivalent.
 *   Hibernate auto-validates the schema against Flyway's SQL (ddl-auto: none).
 *
 * Book ref: Chapter 7 — Memory
 *   "Short-term memory = the conversation window.
 *    Inject the last N turns into the system prompt so the model has context."
 * ══════════════════════════════════════════════════════════════════════════
 */
@Entity
@Table(name = "conversation_message")
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // MERN analogy: the userId field on every document in a MongoDB collection.
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // 'user' | 'assistant' | 'tool'
    // MERN analogy: role field in Vercel AI SDK's CoreMessage type.
    @Column(nullable = false, length = 16)
    private String role;

    // Full text of the message.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Populate createdAt on insert — equivalent of @default(now()) in Prisma.
    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ── No-arg constructor required by JPA ────────────────────────────────
    protected ConversationMessage() {}

    public ConversationMessage(String userId, String role, String content) {
        this.userId = userId;
        this.role = role;
        this.content = content;
    }

    // ── Getters ───────────────────────────────────────────────────────────
    public Long getId()        { return id; }
    public String getUserId()  { return userId; }
    public String getRole()    { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
