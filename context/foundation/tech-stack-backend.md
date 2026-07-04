---
starter_id: spring
package_manager: maven
project_name: plomien-kostrze-api
hints:
  language_family: java
  team_size: solo
  deployment_target: google-cloud-run
  database: cloud-sql-postgres
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge  # changed 2026-07-04, was: manual-promotion
  identity_provider: firebase-auth  # decided 2026-07-04
  llm_provider: openrouter  # decided 2026-07-04
  bootstrapper_confidence: verified
  path_taken: custom
  quality_override: false
  self_check_answers:
    typed: true
    from_official_starter: true
    conventions: true
    docs_current: true
    can_judge_agent: true
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: true
  has_background_jobs: false
---

## Why this stack

Solo developer building the Płomień Kostrze fan portal as an API-first, decoupled
product. This file covers the backend: a Java / Spring Boot API that the Angular SPA
(see tech-stack.md) consumes today and that future Android/iOS apps will reuse — the
load-bearing, longest-lived artifact, which is why an explicit Java/Spring preference
was honored over the JS full-stack default. Spring Boot clears all four agent-friendly
gates (typed, convention-based, popular within the Java family, well-documented) with
verified bootstrapper confidence. Deployment targets Google Cloud per the user's GCP
requirement (decided in `context/foundation/infrastructure.md`, runner-up Render): the
Spring Boot API runs as a containerized **Cloud Run** service in `europe-central2`
(Warsaw), backed by **Cloud SQL for PostgreSQL** as the managed database (private IP via
a Serverless VPC connector) — the data layer the PRD left deferred is now decided here.
To hold the PRD's sub-2s latency NFR against JVM/Spring Boot cold starts (5–15s), the
service runs with `min-instances=1` for the MVP, with Spring Boot AOT / GraalVM
native-image as the planned durable fix. Secrets (DB password, LLM API key, IdP client
secret) live in Secret Manager; CI runs on GitHub Actions with automatic deploy on
merge to master (changed 2026-07-04 from manual promotion — PR review + merge is the
human gate). Auth (admin login plus external-IdP fan login) and the LLM news-generation
pipeline are in MVP scope; payments, realtime, and background jobs are out per the
PRD. Frontend and backend live together in a single monorepo — the Angular app under
frontend/, this Spring API under backend/ — sharing agents, skills, and the context/
foundation at the repo root while staying decoupled across the HTTP API boundary; the
-api suffix on project_name is only a component identity, not a separate repo.
Timeline is explicitly flexible: the MVP may run past the nominal 3 weeks, accepted
for the long-term mobile-reuse payoff.

## Data layer

**Cloud SQL for PostgreSQL** is the managed database (decided in
`context/foundation/infrastructure.md`; the PRD had deliberately deferred the data-layer
choice). PostgreSQL is the default relational fit for this stack — typed, convention-rich,
first-class Spring Data JPA / Hibernate support — and Cloud SQL keeps it co-located with
the Cloud Run service under one GCP project, IAM model, and bill (the user's co-location
preference). It persists the load-bearing domain data: published and draft news posts,
AI-generated proposals awaiting admin acceptance, the minimal fan-identity records from
the external IdP, and the forum threads/replies of the post-MVP fast-follow.

Operational shape (MVP): smallest `db-f1-micro`, no HA, region `europe-central2` (Warsaw),
**private IP** reached from Cloud Run via a Serverless VPC connector (never a public IP).
The DB password lives in Secret Manager. Schema is managed with a forward-only migration
tool (Flyway or Liquibase) — note the infrastructure rollback caveat: a Cloud Run revision
rolls back instantly but a database migration does **not**, so migrations must stay
backward-compatible with the previous app revision. Cost reality: Cloud SQL does **not**
scale to zero, so it is the project's ~$7–10/mo always-on floor cost regardless of traffic;
storage auto-grows but cannot shrink in place.

## AI / LLM layer

**OpenRouter** is the LLM gateway (decided 2026-07-04; the PRD had deferred the choice
to the tech-selection step). It exposes an OpenAI-compatible API over many models
(Claude, Gemini, GPT, and others), so switching the generation model is a config-string
change, not a code change — which directly serves the PRD's riskiest assumption: at
least 75% of generated proposals must be accepted by the admin, and finding the model
with the best Polish sports prose is an experiment, not a foregone conclusion. The
~5% routing fee and the extra network hop were reviewed and accepted: at a few
generations per week the cost is negligible, and the routing overhead (tens of
milliseconds) is invisible next to multi-second model generation; streaming passes
through, so perceived responsiveness matches a direct connection. The single
`OPENROUTER_API_KEY` lives in Secret Manager and is mounted into Cloud Run via
`--set-secrets`. The concrete model (and any pinned upstream provider preference) is
selected and recorded during `/10x-plan` of the `gated-news-generation` slice.

## Identity

**Firebase Authentication** is the external identity provider (decided 2026-07-04) —
the same choice recorded in `tech-stack.md`, shared across both apps. The backend acts
as a stateless resource server: it verifies the Firebase ID token (JWT) from the
Authorization header against Google's public JWKS keys on every request — no sessions,
no passwords stored, per the PRD's NFR. The admin role is granted via a Firebase custom
claim (or a DB allowlist — settled in the `manual-news-publishing` slice plan); fan
login (Google now, Facebook later) reuses the same verification path in the fast-follow.
