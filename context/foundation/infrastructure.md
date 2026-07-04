---
project: Płomień Kostrze
researched_at: 2026-06-30
recommended_platform: Google Cloud (Cloud Run + Firebase Hosting + Cloud SQL)
runner_up: Render
context_type: mvp
tech_stack:
  language: Java (backend) / TypeScript (frontend)
  framework: Spring Boot (backend) / Angular SPA (frontend)
  runtime: JVM 21 (backend container) / static SPA assets (frontend)
---

## Recommendation

**Deploy on Google Cloud:** the Spring Boot API on **Cloud Run**, the static Angular
SPA on **Firebase Hosting**, and the database on **Cloud SQL for PostgreSQL** — all in
`europe-central2` (Warsaw) for a single-region, EU audience. This was a hard,
user-stated requirement across all three foundation contracts (`tech-stack.md`,
`tech-stack-backend.md`, `CLAUDE.md`: `deployment_target: google-cloud-run`), and it is
also the only shortlisted option that satisfies the three forces that actually matter
for this project at once: **co-locate the managed DB** (interview answer), **learn GCP
hands-on** (interview answer), and protect the **decoupled-API / future mobile-reuse**
bet (PRD + stack rationale). Cloud Run is the correct fit for a stateless JVM API with
no realtime/background jobs; Firebase Hosting is the GCP-native, CDN-backed, free-tier
path for a static SPA — strictly better than putting the SPA in an nginx container on
Cloud Run.

## Platform Comparison

Hard filters applied first: **persistent connections not required** (PRD rules out
realtime/background jobs) so no platform was dropped on that axis; **JVM + static-SPA
runtime** is supported everywhere shortlisted. Interview weights: DX over cost,
single-region (edge advantage neutralized), co-located DB required, solo dev new to GCP.

| Platform | CLI-first | Managed/Serverless | Agent-readable docs | Stable deploy API | MCP / Integration | Verdict |
|---|---|---|---|---|---|---|
| **Google Cloud** (Cloud Run + Firebase Hosting + Cloud SQL) | Pass — `gcloud run deploy`, `firebase deploy` | Pass — fully managed, Cloud Run scales to zero (Cloud SQL does **not**) | Pass — `docs.cloud.google.com` is exhaustive (sprawling, but fetchable) | Pass — `gcloud` has predictable exit codes, `--format=json`, non-interactive with flags | Partial — emerging GCP/genai MCP servers, but `gcloud` CLI is the load-bearing path | **Recommended** |
| **Render** | Partial — CLI exists but git-push / `render.yaml` is primary | Pass — managed Postgres; no scale-to-zero on paid (min 1 instance) | Pass | Pass — `render.yaml` IaC + deploy API | Partial — Render MCP server exists | **Runner-up** |
| **Railway** | Pass — strong Railway CLI | Partial — managed + co-located DB, no scale-to-zero, **outage track record** | Pass — dedicated Spring Boot guide | Pass | Pass — Railway MCP server | **3rd** |
| **Fly.io** | Pass — `flyctl` is excellent | Partial — scale-to-zero yes, but **no managed Postgres** (pair external), needs hand-written Dockerfile | Pass | Pass | Partial | Dropped to 4th |

### Shortlisted Platforms

#### 1. Google Cloud (Recommended)

Wins on every constraint that is actually binding here. Cloud Run is a clean fit for a
stateless JVM API (`gcloud run deploy`, scale-to-zero, request-based billing, generous
free tier — ~2M requests/mo). Firebase Hosting gives the Angular SPA a free global CDN
and a one-command deploy without a container. Cloud SQL satisfies the **co-locate the
DB on GCP** answer — one project, one IAM model, one bill, private connectivity to Cloud
Run. And because it's the user's stated platform plus an explicit *learning* goal, the
GCP operational tax is a feature (skill acquisition) rather than pure overhead. The
costs are real and named in the risk register below — they are accepted, not ignored.

#### 2. Render

The strongest off-ramp if the GCP requirement were ever relaxed. Render's managed
Postgres is a genuine production advantage, its DX is the closest thing to old Heroku
(matches the "DX over cost" answer), and it supports Spring Boot containers from Git
without forcing a Dockerfile. Gap vs. the recommendation: it's a second ecosystem (no
co-location with anything else the user runs), no scale-to-zero on paid instances (min 1,
so a flat floor cost like Cloud SQL — but for the *whole* app), and it abandons the
learn-GCP goal.

#### 3. Railway

Best raw developer experience of the four, with a dedicated Spring Boot deployment guide
and co-located managed databases — usage-based pricing that's cheap for low, variable
traffic. Gap vs. the recommendation: a documented recent history of platform outages
makes it a shaky choice for a *public, user-facing* club site, and like Render it walks
away from both the GCP requirement and the learning goal.

## Anti-Bias Cross-Check: Google Cloud

### Devil's Advocate — Weaknesses

1. **JVM cold starts (5–15s) directly violate the latency NFR.** With Cloud Run at
   `min-instances=0`, a low-traffic backend is almost always cold; every admin
   "generate news" action pays a ~10s JVM boot *before* the LLM call begins, breaking
   the PRD's "potwierdzenie bez zauważalnej zwłoki" / "<2s first content" guardrails.
2. **Cloud SQL is the floor cost and is not serverless.** Cheapest `db-f1-micro` is
   ~$7–10/mo **always on**, even at zero traffic; storage auto-grows but cannot shrink
   (you must dump → restore to a smaller instance → delete the original to reclaim it).
3. **GCP's first-timer surface is large and unforgiving:** IAM, service accounts,
   Artifact Registry, ~5 APIs to enable, billing alerts — front-loaded and easy to
   misconfigure (e.g. a public Cloud SQL IP with a weak password).
4. **Cloud Run → Cloud SQL private connectivity** requires the Cloud SQL connector or a
   Serverless VPC Access connector (itself hourly-billed) — a classic stumbling block.
5. **Two-service split = two pipelines, two domains, and CORS** between Firebase Hosting
   and Cloud Run, plus monorepo path-filtered CI that must wire both apps independently.

### Pre-Mortem — How This Could Fail

The admin reports that news-generation "hangs." `min-instances` was left at 0 to chase
the free tier, so the low-traffic backend sat cold — every admin action paid a 10-second
JVM boot before the LLM call even started, making the core tool feel broken and blowing
the latency guardrail. Fixing it with `min-instances=1` surfaced the bill: an
always-warm JVM instance plus an always-on Cloud SQL `db-f1-micro` plus the VPC connector
turned a "scale-to-zero MVP" into a fixed ~$30–40/month a volunteer-run club never
budgeted. Early on, someone gave Cloud SQL a public IP with a weak password "just to get
it working," and it sat exposed for weeks. A logging mishap auto-grew storage that
couldn't be shrunk. The solo dev, new to GCP, burned the deliberately flexible timeline
fighting IAM and VPC config instead of shipping the forum fast-follow. The decoupled-API
bet was sound; the underestimate was GCP's operational tax at this tiny scale.

### Unknown Unknowns

- The real MVP floor is **~$15–40/mo** (Cloud SQL + cold-start mitigation + VPC
  connector), **not $0** — none of those three scale to zero, so Cloud Run's free tier
  is not the whole picture.
- **Firebase project vs GCP project** are linked but live in distinct consoles; the
  mental model trips up first-timers, and "SPA on Firebase Hosting vs nginx-on-Cloud-Run"
  is an explicit fork you must choose deliberately (this doc chooses Firebase Hosting).
- `gcloud run deploy --source` silently uses **Cloud Build + buildpacks**, which enables
  those APIs and incurs build-minute + image-storage charges you didn't explicitly ask for.
- **Spring Boot 3 + GraalVM native image** is the clean cold-start fix (<1s) but changes
  reflection/AOT behavior — the AI/LLM client and some libraries may need native hints;
  it's an architecture decision, not an end-of-project flag flip.
- **europe-central2 (Warsaw)** is not at full feature parity with us-central1; some
  preview features are region-gated, so the closest region can occasionally cost a capability.

## Operational Story

How Google Cloud actually operates day to day for this project. One concrete answer per line.

- **Preview deploys**: Cloud Run gives every revision a stable URL and supports tag-based
  revisions (`gcloud run deploy --tag pr-123 --no-traffic`) for a preview URL with 0%
  traffic; Firebase Hosting has first-class preview channels
  (`firebase hosting:channel:deploy pr-123 --expires 7d`) that mint a temporary,
  shareable SPA URL per PR. CI (GitHub Actions, path-filtered) drives both.
- **Secrets**: store the LLM API key, DB password, and IdP client secret in **Secret
  Manager**; Cloud Run reads them via `--set-secrets` mounted as env vars at deploy time.
  GitHub Actions authenticates to GCP via **Workload Identity Federation** (no long-lived
  JSON key in the repo). Nothing sensitive lives in `.mcp.json` or the repo. Rotation =
  add a new Secret Manager version + redeploy the service.
- **Rollback**: `gcloud run services update-traffic <svc> --to-revisions <PREVIOUS>=100`
  shifts 100% traffic back to a known-good revision in seconds (revisions are immutable
  and retained). Firebase Hosting: `firebase hosting:rollback`. **Caveat:** DB schema
  migrations do **not** roll back automatically — a forward-only migration (e.g.
  Flyway/Liquibase) outlives a code rollback, so plan reversible migrations.
- **Approval**: agent may run unattended — build, deploy a `--no-traffic` revision, tail
  logs, list revisions. **Human-only (panel/CLI by hand):** rotating the primary DB/IdP
  secret, dropping the Cloud SQL instance or database, changing IAM bindings, destructive
  schema changes. Tokens are scoped (Cloud Run Admin + Cloud SQL Client on this project
  only — no billing, no org-level IAM). *Updated 2026-07-04:* production promotion moved
  from "human runs `update-traffic` after merge" to **auto-deploy on merge to `master`**
  — the human gate is PR review + merge; a revision that fails its startup probe never
  takes traffic (see `context/changes/deployment/deployment-plan.md`, Phase 5).
- **Logs**: read-only via
  `gcloud run services logs read <svc> --region europe-central2 --limit 100` for runtime,
  and `gcloud builds log <BUILD_ID>` for the build pipeline; structured JSON is available
  through `gcloud logging read` with a filter when the agent needs to query state.

## Risk Register

| Risk | Source | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| JVM cold start (5–15s) breaks the "<2s / no noticeable delay" NFR | Devil's advocate | H | H | Set `min-instances=1` on the backend (accept the cost) for MVP; plan Spring Boot 3 AOT / GraalVM native image as the durable fix; show a progress indicator on the admin generate flow (the PRD already requires one). |
| Always-on Cloud SQL floor cost (~$7–10/mo, can't scale to zero) | Devil's advocate / Research finding | H | M | Provision the smallest `db-f1-micro`, no HA for MVP; set a **billing budget alert** at $25/$50; review monthly. Accept it as the known floor cost. |
| Real MVP cost is ~$15–40/mo, not free | Unknown unknowns | M | M | Budget alerts + monthly cost review; treat Cloud Run free tier as covering only requests, not DB/connector/warm-instance. |
| Cloud SQL exposed via public IP + weak auth during setup | Pre-mortem | M | H | Use **private IP** + Serverless VPC Access connector (or the Cloud SQL Auth Proxy); never assign a public IP; strong generated password in Secret Manager. |
| Cloud SQL storage auto-grows and can't shrink | Research finding | M | M | Set sensible initial storage; cap log verbosity to DB; if it balloons, dump → restore to a smaller instance → delete original. |
| VPC connector / IAM misconfiguration eats the timeline (new to GCP) | Pre-mortem | M | M | Follow a written setup runbook in the deploy plan; enable APIs explicitly; lean on the learn-GCP goal but timebox investigation and use `gcloud --format=json` for predictable output. |
| Two-service split: CORS + two pipelines + two domains | Devil's advocate | M | M | Define the API base URL + allowed origins as config; wire path-filtered GitHub Actions (`frontend/**` → Firebase, `backend/**` → Cloud Run). *Updated 2026-07-04:* deploy is automatic on merge to `master` (PR review is the gate), superseding the original manual-promotion posture. |
| `gcloud run deploy --source` quietly enables Cloud Build + Artifact Registry billing | Unknown unknowns | M | L | Be deliberate: either accept buildpacks or supply a Dockerfile; clean up old Artifact Registry images; include build APIs in the budget alert. |
| GraalVM native image needs reflection/AOT hints for AI/LLM client | Unknown unknowns | L | M | Treat native image as a planned milestone, not a quick switch; validate the LLM client under native build early if pursued. |
| europe-central2 feature gaps vs us-central1 | Unknown unknowns | L | L | Check feature/region availability before relying on any preview capability; fall back to a GA feature or a different region only if forced. |

## Getting Started

Versions validated against this stack: **Java 21** (`backend/.sdkmanrc`, needs a JDK to
build), **Node 24.18.0** (`frontend/.nvmrc`), Angular SPA (client-side, no SSR per the
PRD → Firebase **Hosting**, *not* App Hosting), Spring Boot container on Cloud Run.

1. **Install + auth the CLIs:** `gcloud components update` then
   `gcloud auth login` and `gcloud config set project <PROJECT_ID>`; install Firebase
   tools with `npm i -g firebase-tools` then `firebase login`. Set region default:
   `gcloud config set run/region europe-central2`.
2. **Enable the APIs once:** `gcloud services enable run.googleapis.com
   sqladmin.googleapis.com secretmanager.googleapis.com cloudbuild.googleapis.com
   artifactregistry.googleapis.com vpcaccess.googleapis.com`.
3. **Provision the DB:** create the smallest non-HA instance
   (`gcloud sql instances create plomien-db --database-version=POSTGRES_16
   --tier=db-f1-micro --region=europe-central2 --no-assign-ip`) and store the password
   in Secret Manager (`gcloud secrets create db-password --data-file=-`).
4. **Deploy the backend:** from `backend/`, build the JAR with `./mvnw -q package` (JDK 21),
   then `gcloud run deploy plomien-api --source . --region europe-central2
   --add-cloudsql-instances <CONNECTION_NAME> --set-secrets DB_PASSWORD=db-password:latest
   --min-instances=1` (min-instances=1 to dodge the JVM cold-start NFR break for MVP).
5. **Deploy the frontend:** from `frontend/`, `npm ci && npm run build`, then
   `firebase init hosting` (point it at the Angular `dist/` output) and
   `firebase deploy --only hosting`. Set the SPA's API base URL to the Cloud Run service
   URL and add that origin to the backend's CORS config. Use
   `firebase hosting:channel:deploy <name>` for PR previews.

## Out of Scope

The following were not evaluated in this research:
- Docker image configuration / Dockerfile authoring (Cloud Run buildpacks vs Dockerfile is noted but not designed here)
- CI/CD pipeline setup (path-filtered GitHub Actions are referenced from CLAUDE.md, not built here)
- Production-scale architecture (multi-region, HA Cloud SQL, DR, SLA commitments)
