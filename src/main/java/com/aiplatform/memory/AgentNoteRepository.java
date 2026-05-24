package com.aiplatform.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for agent notes.
 *
 * MERN/Next.js analogy:
 *   Equivalent to a Prisma AgentNote model query layer.
 *   Method names are parsed by Spring Data into SQL at runtime — no boilerplate.
 *   existsByUserIdAndContent → SELECT COUNT(*)>0 WHERE user_id=? AND content=?
 *   In Prisma: prisma.agentNote.count({ where: { userId, content } }) > 0
 *
 * Book ref: Chapter 7 — Memory
 *   "Idempotent note storage: writing the same note twice must be safe.
 *    Use existence checks + unique constraints as dual guards."
 */
public interface AgentNoteRepository extends JpaRepository<AgentNote, Long> {

    /**
     * Dedup guard: check if an identical note already exists for this user.
     * Called by MemoryService before attempting an insert.
     *
     * MERN analogy: prisma.agentNote.findFirst({ where: { userId, content } }) !== null
     */
    boolean existsByUserIdAndContent(String userId, String content);

    /**
     * Retrieve all notes for a user ordered newest-first.
     *
     * MERN analogy: prisma.agentNote.findMany({ where: { userId }, orderBy: { createdAt: 'desc' } })
     */
    List<AgentNote> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Keyword search across note content for a user.
     * Phase 10 replaces this with a pgvector cosine similarity query.
     *
     * MERN analogy: { content: { $regex: query, $options: 'i' } }
     */
    @Query("SELECT n FROM AgentNote n WHERE n.userId = :userId " +
           "AND LOWER(n.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY n.createdAt DESC")
    List<AgentNote> searchByContent(@Param("userId") String userId,
                                     @Param("query") String query);
}
