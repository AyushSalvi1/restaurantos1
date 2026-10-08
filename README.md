# LIFEOS

A personal operating system. One account, one place to hold tasks, goals, habits, focus sessions,
finances, journal entries, learning and uploaded documents — with an assistant and an analytics layer that
reason over those records instead of over the open internet.

It runs **fully offline with no API key**. The default AI provider is a deterministic local one that
computes answers and insights from your own data. Remote models (Gemini, OpenAI, Ollama) are opt-in.

## Quick start

### Docker Compose

```bash
cp .env.example .env
openssl rand -base64 48            # paste into JWT_SECRET
docker compose up --build
```

Open http://localhost:8081. nginx serves the SPA and proxies `/api` and `/ws` to the backend, so that is
the only origin you need. The backend is on `:8080` for direct API tooling.

Optional profiles:

```bash
docker compose --profile mail up      # Mailpit on :8025, so password-reset links are readable
docker compose --profile ollama up    # local model server
```

### Local development

Requires JDK 21, Maven 3.9+ and Node 22+.

```bash
# backend, on :8080
cd backend && mvn spring-boot:run

# frontend, on :5173
cd frontend && npm ci && npm run dev
```

The dev profile runs Flyway migrations against an embedded database, so there is nothing to install first.

### Demo data

The dev profile enables seeding by default, so `mvn spring-boot:run` gives you an account with content
immediately. In every other profile `SEED_DATA_ENABLED` is off unless you set it.

It creates one demo account with three weeks of realistic records across every domain: goals and
milestones, tasks, habits with daily logs, focus sessions, transactions and a budget, journal entries, a
learning goal, calendar events and one already-indexed document. Seeding is idempotent across restarts and
only ever creates that single account, which holds no real person's information.

```bash
SEED_DATA_ENABLED=true
SEED_EMAIL=demo@lifeos.app
SEED_PASSWORD=Demo-LifeOS-2024
```

Everything LIFEOS shows is derived from records, so with an empty database the derived features have
nothing to say. The seed exists so those features are inspectable without entering a month of data first.

## Verification

```bash
scripts/verify.sh
```

Runs the backend suite (`mvn verify`) then frontend install, lint, typecheck and build. CI does the same and
adds a job that boots the packaged jar against a real MySQL on the prod profile, since that is the only
configuration that applies the MySQL-only migrations.

## What is guaranteed

**The assistant does not invent sources.** Retrieval returns passages from your own documents with
citations. When nothing clears the relevance floor, the answer says so and returns `grounded: false` with
no citations rather than falling back to general model knowledge.

**The offline provider is labelled as such.** `/api/ai/providers` reports the active provider, whether a
remote model is configured, and that offline output is a computation over your records rather than a
model's opinion. The UI repeats this rather than implying a remote model was consulted.

**Where the data is insufficient, it says so.** Analytics responses list their gaps in `dataGaps`
instead of estimating to fill them.

**No dead links.** A server-supplied action path renders as a link only when it resolves to a real route;
otherwise it renders as text.

**Failures are diagnosable.** Every error response carries a request id that matches the server log line.

## Layout

```
backend/   Spring Boot 3, Java 21, JPA + Flyway, JWT security, RAG, analytics, scheduler
frontend/  React 19, TypeScript, Vite, TanStack Query, Tailwind
docs/      ARCHITECTURE.md
scripts/   verify.sh, start-stack.sh, check-toolchain.sh, backup-db.sh
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the pieces fit together.

## Configuration

Every setting has a default in `.env.example`. The one value you must supply is `JWT_SECRET`; the prod
profile refuses to boot without it.

| Variable | Purpose |
| --- | --- |
| `JWT_SECRET` | Signing secret for access tokens. Required in prod. |
| `AI_PROVIDER` | `heuristic` (default, offline), `gemini`, `openai`, or `ollama`. |
| `GEMINI_API_KEY`, `OPENAI_API_KEY` | Only needed if you select that provider. |
| `SEED_DATA_ENABLED` | Creates the demo account. On by default in the dev profile, off elsewhere. |
| `REDIS_ENABLED` | Shared session and rate-limit state. Off means single-node in-memory state. |
| `RATE_LIMIT_ENABLED` | On by default. |
| `SCHEDULER_ENABLED` | Reminders, metric recomputation, insight and prediction regeneration. |
| `STORAGE_ROOT`, `KNOWLEDGE_DIR`, `EXPORT_DIR` | Where uploads and exports are written. |

## Database

MySQL 8. Schema is owned by Flyway: `db/migration/common` applies everywhere, `db/migration/mysql` adds
MySQL-only indexes. Never edit an applied migration; add a new one. The prod profile validates the
migrated schema against the entity mappings at startup, and the same check runs in the test suite."# restaurantos1" 
