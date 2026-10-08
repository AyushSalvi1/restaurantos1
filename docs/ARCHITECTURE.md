# LIFEOS Architecture

LIFEOS is a personal operating system: one account's tasks, goals, habits, focus sessions, finances,
journal, learning and uploaded documents, with an assistant and an analytics layer that reason over those
records. This document describes how the pieces fit together and, where behaviour is deliberately
conservative, why.

## Shape

A modular monolith. One Spring Boot process owns the domain, the HTTP API, the scheduler and the
WebSocket broadcaster. One React SPA is served as static assets. There is no message broker and no
service-to-service traffic, so a change to a domain rule cannot require coordinating a deploy.

```
  Browser
    │  HTTPS / JSON, Bearer JWT
    │  STOMP over WebSocket (/ws) for live notifications
    ▼
  nginx  ── /api, /ws, /actuator ──►  Spring Boot
    │                                  │
    │  static assets                   ├─ security      JWT, method security, rate limiting
    │  (lazy-loaded JS chunks)         ├─ domain        tasks goals habits focus finance
    ▼                                  │                journal calendar learning knowledge
  SPA                                   ├─ analytics     readers → insights, predictions, balance
                                        ├─ ai            provider registry, RAG, assistant
                                        ├─ lifecycle     scheduler
                                        └─ data          JPA → MySQL, Flyway migrations
```

`nginx` proxies `/api`, `/ws` and `/actuator` to the backend, so the browser only ever sees one origin
and no cross-origin credential handling is needed in the normal path. CORS remains configured because
local development serves the Vite dev server on a different port.

## Backend

`com.lifeos`, Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Flyway, MySQL 8 in every deployed
environment and H2 in tests.

| Package | Responsibility |
| --- | --- |
| `controller` | HTTP surface only. Each controller is a thin translation layer over a service. |
| `service` | Domain rules and transactions. Ownership checks live here, not in controllers. |
| `entity`, `repository` | Persistence model and Spring Data queries. |
| `analytics` | Read-only projections over a user's records plus insight and prediction generation. |
| `ai` | Provider abstraction, prompt assembly, the offline provider, and local embeddings. |
| `rag` | Text extraction, chunking, embedding, vector storage, retrieval and grounded answering. |
| `security` | JWT issuance and validation, `CurrentUser`, rate limiting, security error handling. |
| `scheduler` | Periodic reminders, metric recomputation, insight and prediction regeneration, cleanup. |
| `websocket` | STOMP configuration and per-user notification delivery. |
| `exception` | `AppException` factories and the global handler that maps everything to one error shape. |

Ownership is enforced by querying with `userId` in the predicate rather than loading an entity and
checking afterwards. A record that belongs to someone else is reported as `404`, never `403`, so the API
does not confirm that an id exists.

### Request handling

Every response error is the same `ApiError` envelope: a stable machine code, a message written for a
person, optional field violations, the request id, and a timestamp. Client mistakes (`400`), missing or
foreign records (`404`), state conflicts (`409`) and genuine faults (`500`) are distinguished, and a
request id links a user-visible failure to the server log line.

`HttpRequestMethodNotSupportedException` is mapped to `405` rather than falling through to the catch-all
`500`, so a wrong verb is reported as the client error it is.

## Data

Flyway owns the schema. Migrations live in `db/migration/common` and apply to every dialect;
`db/migration/mysql` adds MySQL-only full-text indexes. The prod profile runs `ddl-auto: validate`, so an
entity that declares a column the migrations never create stops the boot rather than surfacing later as a
runtime SQL error on whichever path reads that table first.

`SchemaValidationTests` runs the same validation in the build, and additionally walks the JPA metamodel
against the live schema to confirm every mapped table exists and that entities using the audited
superclass have both audit columns. A missing-column regression is caught by the test suite rather than by
a user.

Audit columns come from two superclasses: `BaseEntity` (`created_at`, `updated_at`) and `CreatedEntity`
(`created_at` only). Deletes are soft where history matters, via `deleted_at`.

Uploads and exports are written to `lifeos.storage`, outside the database. Test runs redirect that to the
system temp directory so fixtures never accumulate in the working tree.

## AI and retrieval

`AIProvider` is a small interface with four implementations: Gemini, OpenAI, Ollama, and `HeuristicProvider`.
The registry picks one from `lifeos.ai.provider`, defaulting to `heuristic`.

**The offline provider is the default and it is deterministic.** It answers by computing over the user's
own records: scores, counts, trends, and rule-derived insights. It is not a language model, and
`/api/ai/providers` reports which provider is active, whether a remote one is configured, and that the
output is a computation rather than a model's opinion. The UI states this rather than implying more.

Retrieval is a four-stage pipeline:

1. `DocumentTextExtractor` pulls plain text from `txt`, `md`, `pdf` and `docx`.
2. `TextChunker` splits it into overlapping passages.
3. `EmbeddingService` embeds each chunk. Remote providers use their hosted embedding model; offline it
   uses `LocalEmbedder`, a hashed bag-of-words projection with sublinear term frequency and L2
   normalisation. It gives real lexical similarity, not semantic understanding, and `LocalEmbedderTests`
   pins that distinction.
4. `InMemoryVectorStore` holds vectors per user and returns candidates by cosine similarity.

`RagService` refuses to answer without evidence: if nothing clears the relevance floor it says so and
returns `grounded: false` with no citations, instead of falling back to general knowledge. This is the
single most important honesty property in the system, and it is covered by tests that assert a citation
is never fabricated.

Ingestion is asynchronous. The upload transaction commits, an application event fires, and indexing runs
on the async executor, so a newly uploaded document is briefly `PENDING`. The demo seeder writes its
document already indexed so retrieval is populated immediately.

The assistant has two grounding paths and reports which one it used: a question about uploaded documents is
answered from retrieved passages and carries citations; anything else is answered from a live context
snapshot of the user's records and returns the scopes that contributed.

## Analytics

Readers (`TaskCountProvider`, `GoalProgressReader`, `HabitConsistencyReader`, `FinanceHealthReader`,
`LearningProgressReader`, `PlanningReader`) each project one dimension. `InsightService` applies explicit
rules over those projections and records the factors behind every insight. `PredictionService` produces
short-horizon forecasts with probabilities and the factors behind them. `BalanceScoreService` computes the
life-balance breakdown against weights the user controls.

Generation is both scheduled (`LifecycleScheduler`, 03:30 and 04:00 by default) and available on demand
from the Analytics page, so a fresh account is not left staring at an empty panel. Where the data cannot
support a conclusion, the response reports it in `dataGaps` instead of estimating.

## Security

Stateless JWT bearer tokens. Short-lived access tokens, rotating refresh tokens stored hashed, and sessions
listable and revocable per account. CSRF is disabled because there is no cookie-borne credential in the API
path. Rate limiting is on by default and configurable per endpoint class. Admin routes and the Prometheus
scrape endpoint require the `ADMIN` role. The prod profile refuses to boot on the default JWT secret.

## Frontend

React 19, TypeScript, Vite, TanStack Query, React Router, Tailwind with shadcn/ui primitives. Pages are
code-split per route. Query caching is the single source of truth; mutations invalidate the affected keys
and roll back optimistic updates on failure. Types in `src/types/api.ts` mirror the backend DTOs, and
nullable fields are declared nullable so a null cannot be rendered as a missing value.

Server-supplied action paths are only rendered as links when they resolve to a real app route. Anything
else is shown as text, so the interface never offers a link that goes nowhere.

## Verification

`scripts/verify.sh` runs the full local gate: backend `mvn verify`, then frontend install, lint, typecheck
and build. CI adds a job that boots the packaged jar against a real MySQL with the prod profile, because
that is the only configuration that exercises the MySQL-only migrations.

The backend suite covers authentication and account isolation, the core journey, knowledge ingestion and
grounded retrieval, offline embedding numerics, schema/entity agreement, and demo seeding. Tests assert
behaviour through the public API rather than internal collaborators.