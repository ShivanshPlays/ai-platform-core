package com.aiplatform.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * JpaMemoryService — Production implementation of MemoryService
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Persists all memory to the relational database via Spring Data JPA.
 * In development: H2 in-memory (with Flyway migrations V1, V2).
 * In production: PostgreSQL (Phase 10).
 *
 * MERN/Next.js analogy:
 *   This is the equivalent of Mastra's LibSQLStore implementation:
 *     const memory = new Memory({ storage: new LibSQLStore({ url: DB_URL }) })
 *   This class is the "LibSQLStore" — it knows how to talk to the DB.
 *   MemoryTool and ChatController only depend on the MemoryService interface
 *   (the abstract "Memory" contract), not on this class.
 *
 * Deduplication strategy for saveNote():
 *   1. existsByUserIdAndContent()  — app-level check (fast path, avoids exception)
 *   2. UNIQUE constraint (V2 migration) — DB-level guard against race conditions
 *   3. DataIntegrityViolationException catch — handles the rare concurrent race
 *   This three-layer approach mirrors the "defence in depth" principle.
 *
 * @Transactional:
 *   Each write method runs in a DB transaction. If anything fails mid-method
 *   the whole thing rolls back — exactly like using a Prisma `$transaction([...])`.
 *
 * Book ref: Chapter 7 — Memory
 *   "The backing store is an implementation detail. Agent code should depend
 *    on the memory abstraction, not on a specific storage technology."
 * ══════════════════════════════════════════════════════════════════════════
 */
// This is the only @Service implementing MemoryService in production, so Spring injects it by default.
// If you add another @Service or @Component implementing MemoryService, you must use @Primary or @Qualifier to resolve ambiguity.
@Service
public class JpaMemoryService implements MemoryService {

    private static final Logger log = LoggerFactory.getLogger(JpaMemoryService.class);

    // Maximum conversation window size injected into prompts.
    // Configurable in application.yml (app.agent.memory.window-size).
    static final int DEFAULT_WINDOW = 8;

    private final ConversationMessageRepository messageRepo;
    private final AgentNoteRepository noteRepo;

    // Constructor injection — MERN analogy: const memoryService = new JpaMemoryService({ db })
    public JpaMemoryService(ConversationMessageRepository messageRepo,
                            AgentNoteRepository noteRepo) {
        this.messageRepo = messageRepo;
        this.noteRepo = noteRepo;
    }

    // ── Conversation (short-term memory) ──────────────────────────────────

    /**
     * Persist one conversation turn.
     *
     * MERN analogy: prisma.conversationMessage.create({ data: { userId, role, content } })
     *
     * Book ref: Chapter 7 — Memory
     *   Saving every turn enables the conversation window injection in the next call.
     */
    @Override
    @Transactional
    public void saveMessage(String userId, String role, String content) {
        messageRepo.save(new ConversationMessage(userId, role, content));
    }

    /**
     * Return the last {@code n} messages for a user in chronological order
     * (oldest → newest), ready to be formatted and injected into a prompt.
     *
     * MERN analogy:
     *   const msgs = await prisma.conversationMessage.findMany({
     *     where: { userId }, orderBy: { createdAt: 'desc' }, take: n
     *   })
     *   msgs.reverse()  // oldest first
     */
    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessage> getRecentMessages(String userId, int n) {
        List<ConversationMessage> desc = messageRepo
                .findTopNByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, n));
        // Reverse so the slice reads oldest → newest (natural conversation order).
        Collections.reverse(desc);
        return desc;
    }

    // ── Agent notes (long-term / episodic memory) ────────────────────────

    /**
     * Persist a free-text agent note — idempotent (duplicate content is silently ignored).
     *
     * Dedup logic:
     *   1. Check existsByUserIdAndContent — avoids the insert on warm path.
     *   2. DB UNIQUE constraint (V2 migration) is the final safety net.
     *   3. DataIntegrityViolationException is swallowed for concurrent races.
     *
     * MERN analogy: prisma.agentNote.upsert({ where: {uq_...}, create: {...}, update: {} })
     */
    @Override
    @Transactional
    public void saveNote(String userId, String noteType, String content) {
        if (content == null || content.isBlank()) return;
        if (noteRepo.existsByUserIdAndContent(userId, content)) {
            log.debug("JpaMemoryService.saveNote: duplicate note ignored for user={}", userId);
            return;
        }
        try {
            noteRepo.save(new AgentNote(userId, noteType, content));
        } catch (DataIntegrityViolationException e) {
            log.debug("JpaMemoryService.saveNote: concurrent duplicate ignored for user={}", userId);
        }
    }

    /**
     * Retrieve notes for a user matching a keyword query (case-insensitive substring).
     * Returns all notes when query is blank.
     *
     * Phase 10 replaces this with pgvector similarity search.
     *
     * MERN analogy: prisma.agentNote.findMany({ where: { userId, content: { contains: query } } })
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> findNotes(String userId, String query) {
        List<AgentNote> notes = (query == null || query.isBlank())
                ? noteRepo.findByUserIdOrderByCreatedAtDesc(userId)
                : noteRepo.searchByContent(userId, query);
        return notes.stream().map(AgentNote::getContent).toList();
    }
}
