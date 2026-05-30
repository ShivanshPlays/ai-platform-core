# GitHub Copilot Instructions — ai-platform-core

## What this repo is

`ai-platform-core` is a **shared AI infrastructure library** extracted from NutritionCoach.
It is consumed by CareerCopilot, NutritionCoach, and any future Spring AI projects in this
workspace. It is **not an application** — it has no `main()`, no web server, no domain logic.

---

## Published artifact

| Field      | Value                                                      |
|------------|------------------------------------------------------------|
| Group      | `com.aiplatform`                                           |
| Artifact   | `ai-platform-core`                                         |
| Version    | `0.0.1-SNAPSHOT`                                           |
| Registry   | `https://maven.pkg.github.com/ShivanshPlays/ai-platform-core` |
| Trigger    | Push to `master` → `.github/workflows/publish.yml` → `mvn deploy` |

New versions are published automatically. Do not manually publish or bump the version
unless explicitly instructed.

---

## Package map

| Package | Classes | Purpose |
|---|---|---|
| `com.aiplatform.guardrail` | `InputGuardrailFilter`, `InputSanitiser`, `OutputModerator`, `RateLimiter`, `CachedBodyRequestWrapper` | Input/output safety, rate limiting, prompt injection detection |
| `com.aiplatform.guardrail` (exceptions) | `PromptInjectionException`, `RateLimitExceededException`, `UnsafeOutputException` | Typed guardrail exceptions |
| `com.aiplatform.memory` | `BaseUserProfile`, `JpaMemoryService`, `MemoryService`, `AgentNote`, `AgentNoteRepository`, `ConversationMessage`, `ConversationMessageRepository` | JPA-backed agent memory — notes + conversation history |
| `com.aiplatform.rag` | `RetrievalTool`, `DocumentIngestionService`, `QueryRewriter`, `RerankingService`, `KeywordEmbeddingModel` | Full RAG pipeline — ingest, rewrite, retrieve, rerank |
| `com.aiplatform.eval` | `BaseEvalService`, `EvalResult`, `EvalAssertionError` | LLM output evaluation harness |
| `com.aiplatform.observability` | `AgentMetricsService` | Agent call metrics via Micrometer |
| `com.aiplatform.api` | `GuardrailExceptionHandler`, `StreamingService` | SSE streaming + exception-to-HTTP mapping |
| `com.aiplatform.config` | `AsyncConfig`, `GuardrailConfig`, `RetryLoggingConfig`, `VectorStoreConfig` | Spring Boot autoconfiguration |
| `com.aiplatform.model` | `UserTier` | Subscription tier enum (`FREE`, `PRO`, `GROWTH`) |

---

## Rules for Copilot

### 1 — This is a library, not an app

- No `@SpringBootApplication`, no `main()`, no embedded server, no domain entities.
- All classes must be annotated for Spring autoconfiguration or be plain components.
- Do not add domain-specific logic (nutrition, career, etc.) — this repo must stay domain-agnostic.
- If a class references a domain concept, it belongs in the consuming app, not here.

### 2 — Backwards compatibility is mandatory

- NutritionCoach and CareerCopilot both depend on this library in production.
- Never rename, remove, or change the signature of a public class or method without
  updating both consuming projects simultaneously.
- Prefer adding new methods over modifying existing ones.
- If a breaking change is unavoidable, bump the minor version and document the migration.

### 3 — Publishing lifecycle

- **To publish a new snapshot:** push to `master`. The `publish.yml` workflow runs `mvn deploy`.
- Do not push directly to `master` for experimental changes — use a feature branch.
- `mvn deploy` is only meaningful in CI (it authenticates via `GITHUB_TOKEN`).
  Running `mvn deploy` locally will fail unless you have `~/.m2/settings.xml` configured.
- For local development and testing, use `mvn install` to put the artifact in `~/.m2`.

### 4 — Authentication pattern for consumers (do not deviate)

Consumers need a `settings.xml` with:

```xml
<server>
    <id>github</id>
    <username>${env.GITHUB_ACTOR}</username>
    <password>${env.GITHUB_TOKEN}</password>
</server>
```

And a `pom.xml` repository entry pointing at:
```
https://maven.pkg.github.com/ShivanshPlays/ai-platform-core
```

In Docker builds, always use BuildKit secrets (verified working in NutritionCoach):

```dockerfile
RUN --mount=type=secret,id=github_token \
    GITHUB_TOKEN=$(cat /run/secrets/github_token) \
    GITHUB_ACTOR=<actor> \
    ./mvnw dependency:go-offline -B --no-transfer-progress
```

**Never use `ARG GITHUB_TOKEN` or `ENV GITHUB_TOKEN`** — these bake the token into an image
layer and will be flagged by Docker security scanners (SecretsUsedInArgOrEnv).

### 5 — What goes in this library (inclusion criteria)

Add a class here only if ALL of the following are true:

- It is useful to at least two different domain applications (NutritionCoach + CareerCopilot minimum)
- It contains zero domain-specific logic or terminology
- It is infrastructure, safety, observability, or AI plumbing — not product logic
- It has a unit test

Examples of good candidates: a new guardrail type, a new RAG retrieval strategy,
a new eval assertion type, a new metrics dimension.

### 6 — What does NOT go in this library

- Domain entities (`Meal`, `JobMatch`, `UserProfile` subclasses with domain fields)
- Business rules (`assertTier()`, subscription gating)
- Application-level controllers or endpoints
- Frontend assets
- Agent classes with domain-specific prompts or actions

### 7 — Every new class needs a test

The `src/test/` directory already has tests for all existing classes.
Do not add production code without a corresponding test in the same package under `src/test/`.

### 8 — NEVER remove the COSINE-DRY-RUN annotation

The comment block in `KeywordEmbeddingModel.java` marked
`[DO-NOT-REMOVE: COSINE-DRY-RUN]` is a permanent pedagogical annotation showing
the mathematical dry run of cosine/dot-product similarity. Do not delete or move it.

---

## How consumers depend on this library

### NutritionCoach (production, verified working)
- `pom.xml`: declares `ai-platform-core` dependency
- `settings.xml`: uses `${env.GITHUB_ACTOR}` / `${env.GITHUB_TOKEN}`
- `Dockerfile`: BuildKit secret mount for `GITHUB_TOKEN`
- `deploy.yml`: passes `secrets: github_token=${{ secrets.GITHUB_TOKEN }}`

### CareerCopilot (Stage 1.1 — pending bootstrap)
- Will use the identical setup as NutritionCoach.
- See `CareerCopilot/.github/copilot-instructions.md` Rule 0 for the exact snippets.

---

## Local development workflow

```bash
# Make changes, run tests
mvn test

# Install locally so NutritionCoach / CareerCopilot can pick it up without CI
mvn install

# Publish to GitHub Packages (CI only — requires GITHUB_TOKEN)
mvn deploy
```

Do not run `mvn deploy` locally unless you have GitHub Packages credentials configured
in `~/.m2/settings.xml`. Use `mvn install` for local iteration.
