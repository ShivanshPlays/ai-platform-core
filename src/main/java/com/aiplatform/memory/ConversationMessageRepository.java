package com.aiplatform.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * ConversationMessageRepository — Spring Data JPA repository
 * ══════════════════════════════════════════════════════════════════════════
 *
 * What is a Spring Data JPA Repository?
 * ──────────────────────────────────────
 * A Spring Data JPA repository is an INTERFACE (not a class) that you extend
 * from JpaRepository<Entity, IdType>.  Spring generates the full implementation
 * at runtime — you never write a single line of SQL or JDBC boilerplate.
 *
 * The inheritance chain:
 *   JpaRepository
 *     └── PagingAndSortingRepository   (adds findAll with Pageable / Sort)
 *           └── CrudRepository         (save, findById, findAll, delete, count, …)
 *
 * So by extending JpaRepository you get ~20 free CRUD methods instantly:
 *   save(entity)       → INSERT or UPDATE (Spring checks if id is null)
 *   findById(id)       → SELECT … WHERE id=?
 *   findAll()          → SELECT * FROM …
 *   delete(entity)     → DELETE …
 *   count()            → SELECT COUNT(*) …
 *   … and more
 *
 * MERN/Next.js analogy:
 *   Think of this interface as a Prisma Model or Mongoose Model:
 *     prisma.conversationMessage.create(...)    ↔  save(new ConversationMessage(...))
 *     prisma.conversationMessage.findMany(...)  ↔  findAll()
 *     prisma.conversationMessage.findUnique(...)↔  findById(id)
 *     prisma.conversationMessage.delete(...)    ↔  delete(entity)
 *   The difference: in Prisma YOU write the query inline; in Spring Data
 *   you declare WHAT you want and Spring generates the HOW at startup.
 *
 * Book ref: Chapter 7 — Memory
 *   "The conversation window = last N messages injected into the system prompt."
 *
 * ══════════════════════════════════════════════════════════════════════════
 * Two query styles used in this repository:
 *
 *   1. Derived Query Methods  (no @Query)  — see findTopNByUserIdOrderByCreatedAtDesc
 *   2. @Query Annotated Methods            — see searchByContent
 *
 * Both are explained in detail on their respective method Javadocs below.
 * ══════════════════════════════════════════════════════════════════════════
 */
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    // ══════════════════════════════════════════════════════════════════════
    // QUERY STYLE 1 — Derived Query Methods (no @Query annotation needed)
    // ══════════════════════════════════════════════════════════════════════
    /**
     * Return the most recent {@code n} messages for a user, newest-first.
     * The caller (MemoryService) reverses the list so the prompt gets the
     * history in oldest → newest (natural reading) order.
     *
     * ── How Derived Query Methods work ───────────────────────────────────
     * Spring Data parses the method NAME as a mini query DSL:
     *
     *   findTop N By UserId OrderBy CreatedAt Desc
     *   │────┤ │──┤ │──────────────┤ │───────────────────────────────────┤
     *   │    │ │  │ │WHERE clause  │ │ORDER BY clause
     *   │    │ │  │ └─ WHERE user_id = ?
     *   │    │ └──┘
     *   │    └─ LIMIT N  (N comes from the Pageable parameter)
     *   └─ SELECT *
     *
     * Generated SQL (approximately):
     *   SELECT * FROM conversation_message
     *   WHERE user_id = ?
     *   ORDER BY created_at DESC
     *   LIMIT ?                        ← taken from PageRequest.of(0, n)
     *
     * The Pageable parameter is the key to making N dynamic:
     *   PageRequest.of(0, 8)  → fetch page 0, up to 8 rows
     *   PageRequest.of(0, 20) → fetch page 0, up to 20 rows
     * Without Pageable, "TopN" must be a fixed number baked into the name
     * (e.g., findTop8By...).  With Pageable, N is decided at call time.
     *
     * Keywords you can combine in method names:
     *   find / read / get / query / count / exists / delete
     *   Top / First (limit)
     *   By<Field>    (WHERE clause)
     *   And / Or     (combine conditions)
     *   OrderBy<Field>Asc|Desc
     *   Between / LessThan / GreaterThan / Like / Containing / In / IsNull …
     *
     * Examples of other derived methods you COULD add here for free:
     *   List<ConversationMessage> findByUserIdAndRole(String userId, String role);
     *     → WHERE user_id=? AND role=?
     *   long countByUserId(String userId);
     *     → SELECT COUNT(*) WHERE user_id=?
     *   void deleteByUserIdAndCreatedAtBefore(String userId, LocalDateTime cutoff);
     *     → DELETE WHERE user_id=? AND created_at < ?
     *
     * When NOT to use derived methods:
     *   Once the method name becomes longer than ~5 keywords, switch to @Query.
     *   Complex joins, subqueries, native SQL, or LIKE patterns with computed
     *   values all need @Query (see Style 2 below).
     *
     * MERN analogy:
     *   // Prisma equivalent:
     *   prisma.conversationMessage.findMany({
     *     where:   { userId },
     *     orderBy: { createdAt: 'desc' },
     *     take:    n
     *   })
     *
     * Book ref: Chapter 7 — Memory
     *   Sliding window pattern: fetch last N turns, reverse, inject into prompt.
     */
    List<ConversationMessage> findTopNByUserIdOrderByCreatedAtDesc(
            String userId, org.springframework.data.domain.Pageable pageable);


    // ══════════════════════════════════════════════════════════════════════
    // QUERY STYLE 2 — @Query Annotated Methods (explicit JPQL / SQL)
    // ══════════════════════════════════════════════════════════════════════
    /**
     * Keyword search across message content for a user (case-insensitive).
     * Phase 10 replaces this with a pgvector similarity query.
     *
     * ── How @Query Annotated Methods work ────────────────────────────────
     * When the method name DSL is not expressive enough, you write the query
     * yourself using @Query.  The language here is JPQL (Java Persistence
     * Query Language), not SQL.
     *
     * JPQL vs SQL:
     *   SQL  operates on TABLES  and COLUMNS:  SELECT * FROM conversation_message WHERE …
     *   JPQL operates on ENTITIES and FIELDS:  SELECT m FROM ConversationMessage m WHERE …
     *                                                  ↑ the @Entity class name, not the table
     *                                                                           ↑ the Java field name, not the column
     *
     * Breaking down this query:
     *   SELECT m                                ← return the full ConversationMessage object
     *   FROM ConversationMessage m              ← FROM the @Entity class (Spring resolves the table)
     *   WHERE m.userId = :userId                ← m.userId is the Java field (mapped to user_id column)
     *   AND LOWER(m.content) LIKE               ← LOWER() for case-insensitive match
     *       LOWER(CONCAT('%', :query, '%'))      ← wraps the param in % wildcards: %query%
     *   ORDER BY m.createdAt DESC               ← newest first
     *
     * Named parameters (:userId, :query) are bound via @Param("userId") etc.
     * Positional parameters (?1, ?2) are an older alternative — avoid them
     * because they break if you reorder parameters.
     *
     * When to prefer @Query over derived methods:
     *   • Complex WHERE clauses (LOWER, CONCAT, subqueries, CASE …)
     *   • JOINs across multiple entities
     *   • Aggregate functions (SUM, AVG, GROUP BY)
     *   • Native SQL (add nativeQuery=true) — useful for DB-specific features
     *     like pg_similarity() or H2 full-text search
     *
     * nativeQuery=true example (Phase 10 pgvector):
     *   @Query(value = "SELECT * FROM conversation_message WHERE embedding <=> :vec LIMIT :n",
     *          nativeQuery = true)
     *   List<ConversationMessage> findBySimilarity(@Param("vec") float[] vec, @Param("n") int n);
     *
     * MERN analogy:
     *   // Mongoose:
     *   ConversationMessage.find({
     *     userId,
     *     content: { $regex: query, $options: 'i' }
     *   }).sort({ createdAt: -1 })
     *
     *   // Prisma (no built-in ILIKE, so raw SQL needed):
     *   prisma.$queryRaw`SELECT * FROM conversation_message
     *                    WHERE user_id=${userId}
     *                    AND LOWER(content) LIKE LOWER(${'%'+query+'%'})
     *                    ORDER BY created_at DESC`
     *
     * Book ref: Chapter 7 — Memory
     *   Keyword retrieval is step 1; Phase 10 upgrades to semantic (vector)
     *   retrieval — the same @Query pattern applies, just swap in a pgvector
     *   native query.
     */
    @Query("SELECT m FROM ConversationMessage m WHERE m.userId = :userId " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY m.createdAt DESC")
    List<ConversationMessage> searchByContent(@Param("userId") String userId,
                                               @Param("query") String query);
}
