package com.aiplatform.memory;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * MemoryService — backing-store abstraction for all agent memory
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Why an interface?
 *   By coding to an interface rather than a concrete class, the backing store
 *   can be swapped without changing any consumer (MemoryTool, ChatController,
 *   CoachAgent).  Production uses JpaMemoryService; tests use
 *   InMemoryMemoryService (no Spring context, no DB).
 *
 *   This maps directly to Mastra's backing-store pattern:
 *     const memory = new Memory({ storage: new LibSQLStore({ url }) })
 *     // or:
 *     const memory = new Memory({ storage: new InMemoryStorage() })
 *   In Spring, swapping is done by providing a different @Service bean that
 *   implements this interface.
 *
 * Three memory types (Book ref: Chapter 7 — Memory):
 *   saveMessage / getRecentMessages  → short-term / conversation window
 *   saveNote / findNotes             → long-term / episodic (agent notes)
 *   UserProfile updates              → long-term / semantic (via JpaMemoryService)
 *
 * Implementations:
 *   JpaMemoryService      — production; persists to H2 / PostgreSQL via JPA
 *   InMemoryMemoryService — tests; in-memory only, no Spring context needed
 *
 * MERN/Next.js analogy:
 *   interface Memory {
 *     saveMessage(userId, role, content): Promise<void>
 *     getRecentMessages(userId, n): Promise<Message[]>
 *     saveNote(userId, noteType: string, content): Promise<void>  // noteType = 'COACHING'|'RESEARCH'|...
 *     findNotes(userId, query): Promise<string[]>
 *   }
 *
 * Book ref: Chapter 7 — Memory
 *   "The backing store is an implementation detail. Agent code should depend
 *    on the memory abstraction, not on a specific storage technology."
 * ══════════════════════════════════════════════════════════════════════════
 */
public interface MemoryService {

    /**
     * Persist one conversation turn.
     *
     * @param userId  the user who owns this message
     * @param role    'user' | 'assistant' | 'tool'
     * @param content the message text
     */
    void saveMessage(String userId, String role, String content);

    /**
     * Return the last {@code n} messages for a user in chronological order
     * (oldest → newest), ready to be injected into a prompt.
     *
     * @param userId the user whose history to fetch
     * @param n      maximum number of messages to return
     * @return messages sorted oldest-first
     */
    List<ConversationMessage> getRecentMessages(String userId, int n);

    /**
     * Persist a free-text agent note — idempotent (duplicates silently ignored).
     *
     * @param userId   the user this note belongs to
     * @param noteType classifier string for this note (e.g. NoteType.COACHING.name())
     * @param content  the note text
     */
    void saveNote(String userId, String noteType, String content);

    /**
     * Retrieve notes for a user matching a keyword query (case-insensitive substring).
     * Returns all notes when query is blank/null.
     *
     * @param userId the user whose notes to search
     * @param query  case-insensitive substring; empty/null returns all notes
     * @return matching note content strings, newest-first
     */
    List<String> findNotes(String userId, String query);
}
