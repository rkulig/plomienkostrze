---
project: Płomień Kostrze
plan_created: 2026-06-30
plan_for: First production deployment to Google Cloud
source_contract: context/foundation/infrastructure.md
recommended_platform: Google Cloud (Cloud Run + Firebase Hosting + Cloud SQL)
region: europe-central2 (Warsaw)
github_repo: rkulig/plomienkostrze
context_type: mvp
status: "Phase A executed 2026-07-02; Phase B + WIF/IAM executed 2026-07-04 — remaining: workflow rewrite (Phase 5) + Phase C code"
decisions:
  db_infra: ACTIVE since 2026-07-04 — trigger satisfied by the E2E test-flow milestone (Phase C); supersedes "deferred"
  build_method: explicit multi-stage Dockerfile (backend/Dockerfile)
  ci_flow: auto-deploy on merge to master (2026-07-04 — supersedes manual promotion; PR review + merge becomes the human gate)
  e2e_probe: test endpoint SPA → API → DB (simple text saved to a test table) proves the full data path
  deliverable: written runbook only (no GCP mutations performed by this plan)
---

## Context

This plan operationalizes the platform decision recorded in
`context/foundation/infrastructure.md` into an executable, checkbox-tracked deploy
runbook. It exists because the platform is chosen but **nothing has been deployed yet**
— there is no `context/deployment/` artifact, no `.github/workflows/`, no Dockerfile, no
`firebase.json`, and no GCP resources.

**Ground truth from codebase exploration (2026-06-30):** both apps are bare scaffolds.

- **Backend** (`backend/`): Spring Boot 4.1.0, Java 21, Maven wrapper. Dependencies are
  `spring-boot-starter-webmvc` + devtools **only**. No PostgreSQL driver, no JPA, no
  Flyway/Liquibase, no Spring Security/OAuth, no Actuator, no LLM client, no controllers,
  no CORS, no Dockerfile. `application.properties` contains only `spring.application.name`.
- **Frontend** (`frontend/`): Angular 22, `@angular/build:application` builder, output at
  `dist/plomien-kostrze/browser/` (note the nested `browser/`). No `firebase.json`,
  no `.firebaserc`, no `src/environments/`, no `HttpClient`. Empty routes.
- **Repo**: remote `github.com/rkulig/plomienkostrze`, clean `.gitignore` (no committed
  secrets), no CI.

**Consequence (the load-bearing decision):** infrastructure.md describes the *target*
topology including Cloud SQL, a Serverless VPC connector, and Secret Manager for the
DB/LLM/IdP secrets. But **the code has nothing that connects to any of them yet.**
Provisioning an always-on `db-f1-micro` + hourly-billed VPC connector now would commit the
~$15–40/mo floor cost (infrastructure.md "Unknown Unknowns") to resources nothing uses.

**Therefore this plan is split:**
- **Phase A (now):** ship the *stateless* API to Cloud Run + the SPA to Firebase Hosting +
  keyless CI, scale-to-zero, ~$0 floor.
- **Phase B (deferred):** Cloud SQL + VPC connector + Secret Manager, triggered the moment
  backend code adds persistence, the LLM client, or external-IdP auth.

Intended outcome: a public, reproducible MVP deployment with a human gate before any
production traffic shift, matching infrastructure.md's operational story.

**Update 2026-07-04 — Phase A shipped; next stage decided.** Both apps are live
(SPA `https://plomien-kostrze.web.app`, API `https://plomien-api-714793368062.europe-central2.run.app`).
The workflow files exist (committed on `M1L5-addDeployFiles`) but CI is not yet
operational: the WIF pool + `github-oidc` provider exist in GCP (verified 2026-07-04),
while the `gh-deployer` service account they reference does **not** — both workflows
would fail at the auth step today. Cloud SQL remains untouched (`sqladmin` API disabled,
no instances). Three decisions extend the plan:

1. **Phase B is now active** — the E2E test-flow milestone (Phase C, below) is the
   persistence feature that satisfies its trigger.
2. **CI flow changes to auto-deploy on merge** — merging a PR to `master` builds,
   deploys, and promotes to live traffic automatically. This supersedes the
   manual-promotion posture in `CLAUDE.md` / `infrastructure.md` / `tech-stack*.md`
   (all updated 2026-07-04). The human gate moves from "promote by hand after merge"
   to "review + merge the PR"; a failed-to-start revision still never takes traffic
   (Cloud Run keeps serving the previous revision), which is the safety net that makes
   auto-promotion acceptable.
3. **Phase C (new)** — a test endpoint proving the full data path: the SPA accepts a
   simple text, POSTs it to the API, the API persists it in a test table.

---

## Legend

- `[ ]` step to do · `[x]` done
- 🔒 **Human-only / manual gate** — irreversible or account/billing/secret-touching;
  do by hand, never let the agent run unattended (per infrastructure.md "Approval" axis).
- 🤖 agent-safe — deterministic, scriptable, reversible.
- ⚠️ **edge case / extra support** — a known stumbling block with its workaround inline.

---

## Prerequisites (one-time, manual)

All satisfied — verified against live GCP state on 2026-07-04.

- [x] 🔒 GCP account with **billing enabled** and a project created
  (suggested id: `plomien-kostrze`; capture the real `PROJECT_ID`).
  > `PROJECT_ID=plomien-kostrze`, project number `714793368062`, billing account linked.
- [x] 🔒 **Billing budget alert** set at **$25 / $50** before any resource is created
  (infrastructure.md risk: "Real MVP cost is ~$15–40/mo, not free").
  `gcloud billing budgets create` or the Billing console.
  > Budget **"plomien-kostrze MVP" ($50)** exists on the project's billing account.
- [x] 🔒 Firebase project **linked to the same GCP project** (Firebase console → Add
  project → *select the existing GCP project*, do **not** create a new one).
  ⚠️ Firebase and GCP are the same project but two consoles — first-timer trap from
  infrastructure.md "Unknown Unknowns". Linking them now avoids a split project later.
  > Confirmed: Hosting live at `plomien-kostrze.web.app` under the same project.
- [x] 🤖 Install/auth CLIs:
  - `gcloud components update`
  - `gcloud auth login` · `gcloud config set project <PROJECT_ID>`
  - `gcloud config set run/region europe-central2`
  - `npm i -g firebase-tools` · `firebase login`
  > Both CLIs authed (`gcloud` queries the project; `firebase deploy` already ran).
- [x] 🤖 Confirm toolchains: backend `sdk use java 21.0.11-tem` (JDK, per `.sdkmanrc`);
  frontend `nvm use` (Node 24.18.0, per `.nvmrc` — system default 24.14.0 is too old
  for the Angular 22 CLI).
  > Proven by the executed Phase 2/3 builds.

---

## Phase 0 — Local artifacts (no GCP mutations) 🤖

These are committed code/config changes that make the scaffolds deployable. All are
reversible and reviewable in a PR. **None touch GCP.**

- [x] **`backend/Dockerfile`** — multi-stage (chosen build method). Pin both base images.
  ```dockerfile
  # ---- build ----
  FROM maven:3.9-eclipse-temurin-21 AS build
  WORKDIR /app
  COPY .mvn/ .mvn/
  COPY mvnw pom.xml ./
  RUN ./mvnw -q -B dependency:go-offline
  COPY src ./src
  RUN ./mvnw -q -B clean package -DskipTests

  # ---- run ----
  FROM eclipse-temurin:21-jre
  WORKDIR /app
  COPY --from=build /app/target/*.jar app.jar
  # Cloud Run injects $PORT (default 8080); honor it explicitly.
  ENV JAVA_OPTS=""
  EXPOSE 8080
  ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
  ```
  ⚠️ Cloud Run sets `PORT=8080` and Spring already defaults to 8080, so it works without
  the override — but binding `${PORT}` explicitly is the documented-correct contract and
  protects against Cloud Run changing the injected port.
- [x] **`backend/.dockerignore`** — exclude `target/`, `.git`, `*.md`, IDE dirs so the
  build context stays small.
- [x] **`backend/src/main/resources/application.properties`** — add Cloud-Run-friendly config:
  ```properties
  server.port=${PORT:8080}
  # CORS allowed origin(s) — set to the Firebase Hosting URL once known (Phase 4).
  app.cors.allowed-origins=${ALLOWED_ORIGINS:http://localhost:4200}
  ```
- [x] **Add Actuator** to `backend/pom.xml` (`spring-boot-starter-actuator`) and expose
  health: `management.endpoints.web.exposure.include=health`. Gives Cloud Run a real
  `/actuator/health` liveness/startup probe target instead of a 404.
  ⚠️ Optional for first boot (Cloud Run defaults to TCP-port readiness), but cheap
  insurance and required once you add a DB (health then reflects DB connectivity).
- [x] **CORS config** — add a `WebMvcConfigurer` (or, later, Spring Security CORS) reading
  `app.cors.allowed-origins`. Until the SPA actually calls the API this is inert, but wire
  it now so Phase 4 is a config value, not a code change.
- [x] **A minimal health/ping controller** in `com.plomienkostrze.web`
  (e.g. `GET /api/ping` → `{"status":"ok"}`) so the deploy has something non-404 to verify.
- [x] **`frontend/firebase.json`** — ⚠️ **critical path gotcha**: the `@angular/build:application`
  builder nests output under `browser/`. Public dir **must** be `dist/plomien-kostrze/browser`,
  not `dist/plomien-kostrze`.
  ```json
  {
    "hosting": {
      "public": "dist/plomien-kostrze/browser",
      "ignore": ["firebase.json", "**/.*", "**/node_modules/**"],
      "rewrites": [{ "source": "**", "destination": "/index.html" }]
    }
  }
  ```
  ⚠️ The `rewrites` → `/index.html` is mandatory for an Angular SPA — without it, deep
  links / refresh on any route 404 because Firebase looks for a static file.
- [x] **`frontend/.firebaserc`** — `{ "projects": { "default": "plomien-kostrze" } }`.
- [x] **`frontend/src/environments/`** — create `environment.ts` /
  `environment.production.ts` with `apiBaseUrl`. Add `provideHttpClient()` to
  `app.config.ts`. Wire `fileReplacements` in `angular.json` production config.
  (Value filled in Phase 4 once the Cloud Run URL exists.)
- [x] **`backend/.gcloudignore`** — even with a Dockerfile build, keep the uploaded source
  context lean (exclude `target/`, docs, `.git`).

---

## Phase 1 — Enable APIs & Artifact Registry 🤖 (one-time)

- [x] Enable exactly the APIs Phase A needs (defer `sqladmin`/`vpcaccess` to Phase B):
  ```
  gcloud services enable run.googleapis.com cloudbuild.googleapis.com \
    artifactregistry.googleapis.com iamcredentials.googleapis.com
  ```
  ⚠️ infrastructure.md's bulk-enable line includes `sqladmin.googleapis.com` and
  `vpcaccess.googleapis.com` — intentionally **omitted here** because Phase A has no DB.
- [x] Create an Artifact Registry Docker repo in-region (explicit, so we control image
  storage — infrastructure.md note: `--source` silently creates one and bills for it):
  ```
  gcloud artifacts repositories create plomien \
    --repository-format=docker --location=europe-central2
  ```

---

## Phase 2 — Deploy backend to Cloud Run 🤖 (with 🔒 promotion gate)

- [x] Build & deploy a **no-traffic** revision first (agent-safe per infrastructure.md
  "Approval"). Using `--source .` with the committed `backend/Dockerfile`:
  > **Executed 2026-07-02, with two deviations:** (1) `--no-traffic` is **not supported when
  > creating a new service**, so the first revision (`plomien-api-00001`) took 100% traffic
  > directly — acceptable per the plan's fallback (it only serves `/api/ping`); no-traffic
  > applies from the 2nd deploy on. (2) Built **locally via Docker → `docker push`** to the
  > `plomien` AR repo, not `gcloud builds submit` — Cloud Build hit `PERMISSION_DENIED`
  > (fresh-project default-SA/logs-bucket quirk) and local Docker's `credsStore:"desktop"`
  > was gpg-locked (worked around with a temp `DOCKER_CONFIG`). CI (Phase 5) builds on the
  > runner for the same reason. Image: `…/plomien/plomien-api:v1`.
  ```
  cd backend
  gcloud run deploy plomien-api --source . \
    --region europe-central2 \
    --allow-unauthenticated \
    --min-instances=0 \
    --no-traffic --tag staging
  ```
  - ⚠️ **`--min-instances=0` is deliberate for bring-up** (scale-to-zero, ~$0). The
    infrastructure.md risk register sets `min-instances=1` to dodge the JVM cold-start
    NFR — **flip to 1 only when the real admin LLM-generation flow ships** (it's
    latency-sensitive; an empty `/api/ping` is not). Tracked as a Phase-A→feature trigger
    below.
  - ⚠️ `--allow-unauthenticated` makes the API public (correct: the public news list needs
    no login per PRD). Admin auth is enforced *in-app* via the external IdP later, not via
    Cloud Run IAM.
- [x] Smoke-test the **tagged staging URL** (`https://staging---plomien-api-...run.app/api/ping`).
  > Smoke-tested the **live service URL** instead (no `staging` tag on the first deploy):
  > `/api/ping` → `{"status":"ok"}` 200, `/actuator/health` → `UP` 200.
- [x] 🔒 **Promote to production traffic** (human-only — first production exposure):
  > Collapsed into the first deploy (the new-service revision necessarily served traffic).
  > The explicit `update-traffic` gate applies to all subsequent revisions.
  ```
  gcloud run services update-traffic plomien-api --to-latest --region europe-central2
  ```
- [x] Capture the stable service URL → it becomes `apiBaseUrl` in Phase 4.
  > `https://plomien-api-714793368062.europe-central2.run.app`

⚠️ **Edge case — JVM cold start under buildpacks vs Dockerfile:** we chose a Dockerfile to
avoid surprise Cloud Build/Artifact Registry billing and to control the JRE. First build is
slow (downloads Maven deps); subsequent builds reuse layers. If build minutes become a
concern, pre-build the jar locally and `COPY` it instead of building in-image.

---

## Phase 3 — Deploy frontend to Firebase Hosting 🤖 (with 🔒 promotion gate)

- [x] Build the SPA: `cd frontend && nvm use && npm ci && npm run build`.
  Verify `dist/plomien-kostrze/browser/index.html` exists.
  > Built with `apiBaseUrl` already set to the Cloud Run URL (baked in before this build, so
  > Phase 4 needed no frontend rebuild). Output confirmed at `dist/plomien-kostrze/browser/`.
- [x] Deploy to a **preview channel** first (shareable, non-prod — infrastructure.md
  "Preview deploys"):
  ```
  firebase hosting:channel:deploy preview-initial --expires 7d
  ```
- [x] Open the preview URL, confirm the app loads and SPA deep-link rewrite works.
  > Preview `https://plomien-kostrze--preview-initial-3zefzghp.web.app`: root 200, deep link
  > `/some/deep/route` served `index.html` (200, not 404) — rewrite confirmed.
- [x] 🔒 **Promote to the live channel** (human-only):
  > Live at `https://plomien-kostrze.web.app` (root 200).
  ```
  firebase deploy --only hosting
  ```

⚠️ **Edge case — `firebase init hosting` overwriting files:** do **not** run the
interactive `firebase init hosting` after committing `firebase.json`/`.firebaserc` — it
will offer to overwrite them and to scaffold an `index.html`, clobbering the Angular build
config. The two committed files are sufficient; skip init.

---

## Phase 4 — Wire frontend ↔ backend 🤖

- [x] Set `apiBaseUrl` in `frontend/src/environments/environment.production.ts` to the
  Cloud Run service URL from Phase 2. *(Done before the Phase 3 build — uncommitted on master.)*
- [x] Set the backend's `ALLOWED_ORIGINS` env var to the **live Firebase Hosting URL**
  (`https://<PROJECT_ID>.web.app` and/or the custom domain) via
  `gcloud run services update plomien-api --update-env-vars ALLOWED_ORIGINS=https://<PROJECT_ID>.web.app`.
- [x] Rebuild + redeploy frontend (Phase 3) and confirm a real cross-origin call succeeds.
  > No frontend rebuild needed (URL was baked before the Phase 3 build). Set via
  > `gcloud run services update … --update-env-vars "^##^ALLOWED_ORIGINS=https://plomien-kostrze.web.app,https://plomien-kostrze.firebaseapp.com"`
  > (rev `plomien-api-00002`). CORS verified by curl preflight: **200 + `Access-Control-Allow-Origin`**
  > for `web.app`, **403** for a disallowed origin. (In-app SPA→API ping widget deferred — no
  > component calls the API yet.)

⚠️ **Edge case — CORS preflight:** browsers send an `OPTIONS` preflight for non-simple
requests. The `WebMvcConfigurer` must allow the needed methods/headers, and Cloud Run must
not strip them. Test with the browser devtools Network tab, not just `curl`.
⚠️ **Edge case — two domains:** Firebase serves `.web.app` **and** `.firebaseapp.com`; if
you add a custom domain later, all live origins must be in `ALLOWED_ORIGINS`.

---

## Phase 5 — CI/CD: keyless GitHub Actions, path-filtered, auto-deploy on merge 🤖 + 🔒

Implements infrastructure.md "Secrets" (Workload Identity Federation, no JSON key in repo)
and the path-filtered mandate. **Decision 2026-07-04: auto-deploy on merge** — merging a
PR to `master` builds, deploys, and promotes to live automatically; the human gate is PR
review + merge, not a post-merge promotion command. (Supersedes the original
manual-promotion posture; CLAUDE.md / infrastructure.md / tech-stack*.md updated.)

- [x] 🔒 **Workload Identity Federation** (one-time, IAM-touching — do by hand).
  > **Completed 2026-07-04 (by hand):** pool `github` + provider `github-oidc` pre-existed
  > (provider condition verified: pins `rkulig/plomienkostrze`); `gh-deployer` SA created
  > with exactly the four project-level roles below + `workloadIdentityUser` binding.
  ```
  # Deployer service account + scoped roles (Cloud Run + Artifact Registry + Firebase Hosting)
  gcloud iam service-accounts create gh-deployer
  # roles/run.admin, roles/iam.serviceAccountUser, roles/artifactregistry.writer,
  # roles/firebasehosting.admin — grant on the project, NOT org level.

  # Let the repo's tokens impersonate the deployer SA
  gcloud iam service-accounts add-iam-policy-binding \
    gh-deployer@plomien-kostrze.iam.gserviceaccount.com \
    --role=roles/iam.workloadIdentityUser \
    --member="principalSet://iam.googleapis.com/projects/714793368062/locations/global/workloadIdentityPools/github/attribute.repository/rkulig/plomienkostrze"
  ```
  ⚠️ Scope the SA to **this project only** (no billing, no org IAM) per infrastructure.md
  "Production-access boundary". Confirm the provider's `--attribute-condition` pins
  `rkulig/plomienkostrze` — without it any GitHub repo could mint tokens for your pool.
- [x] **`.github/workflows/backend.yml`** — split by event (currently both `push` and
  `pull_request` deploy a no-traffic revision):
  > Done 2026-07-04 on `M1L5-addDeployFiles`: `build-test` job (PR, no GCP) +
  > `deploy` job (push to master, `mvnw verify` gates, deploy takes traffic).
  - `pull_request` on `backend/**`: **build + test only** (`./mvnw -B verify` +
    `docker build`) — no GCP auth needed, so fork PRs can't touch the WIF pool.
  - `push` to `master` on `backend/**`: build & push image, then
    `gcloud run deploy plomien-api --image … ` **without `--no-traffic`** — the new
    revision takes 100% traffic once it passes its startup probe.
  ⚠️ Safety net that makes auto-promotion acceptable: a revision that fails to start
  never receives traffic — Cloud Run keeps serving the previous revision and the
  workflow step exits non-zero (visible in the PR's checks history).
- [x] **`.github/workflows/frontend.yml`** — same split:
  > Done 2026-07-04 on `M1L5-addDeployFiles`: `build` job (PR, no GCP) +
  > `deploy` job (push to master → live Hosting channel).
  - `pull_request` on `frontend/**`: `npm ci && npm run build` only (optionally a
    preview channel — requires WIF, so skip for fork PRs).
  - `push` to `master` on `frontend/**`: build, then `firebase deploy --only hosting`
    (live channel, automatic).
  ⚠️ firebase-tools in CI reads Application Default Credentials — point
  `GOOGLE_APPLICATION_CREDENTIALS` at the credentials file emitted by
  `google-github-actions/auth@v2` (do **not** use the deprecated `firebase login:ci` token).
- [ ] ⚠️ **Monorepo path-filter gotcha:** a PR touching only `context/` or `CLAUDE.md`
  should trigger **neither** pipeline. Verify the `paths:` filters exclude shared/root files
  so doc changes don't trigger deploys.
- [ ] ⚠️ **Auto-deploy × DB migrations:** once Phase B lands, a merged migration reaches
  production without a manual gate. Migrations must be additive/backward-compatible with
  the previous revision (see Phase B); destructive schema changes (drop/rename) remain
  🔒 human-planned, two-step (expand → contract in separate releases).

---

## Phase B (ACTIVE since 2026-07-04) — Cloud SQL + Secret Manager 🔒-heavy

**Trigger to start this phase:** ~~the backend PR that first adds a PostgreSQL driver + JPA,
the LLM client, or external-IdP auth~~ → **satisfied**: Phase C (the E2E test-flow
endpoint) is that persistence feature. Provision B, then land C.

- [x] Enable the deferred APIs: `gcloud services enable sqladmin.googleapis.com secretmanager.googleapis.com`
  (add `vpcaccess.googleapis.com` only if the fallback connector below is needed).
  > Done 2026-07-04 (+ `servicenetworking`, `compute` — required for Private Services
  > Access, which `--no-assign-ip` needs: allocated range `google-managed-services-default`
  > /16 + VPC peering on network `default`).
- [x] 🔒 Create the DB (smallest, no HA, **private IP only**):
  > Done 2026-07-04: `plomien-db` RUNNABLE, private IP `10.10.0.3`, database `plomien`,
  > user `plomien`; password generated straight into Secret Manager (`db-password` v1,
  > `secretAccessor` granted to the runtime SA), never displayed anywhere.
  ```
  gcloud sql instances create plomien-db --database-version=POSTGRES_16 \
    --tier=db-f1-micro --region=europe-central2 --no-assign-ip
  ```
  ⚠️ **Never assign a public IP** (infrastructure.md pre-mortem: "public Cloud SQL IP +
  weak password sat exposed for weeks"). Generate a strong password into Secret Manager,
  never into the repo or chat:
  `gcloud secrets create db-password --data-file=-` (pipe a generated value).
- [x] 🔒 Private connectivity Cloud Run → Cloud SQL — **prefer Direct VPC egress** (GA,
  no hourly-billed connector; deviation from infrastructure.md's VPC-connector wording,
  motivated by its own cost risk register): deploy the service with
  `--network=default --subnet=default --vpc-egress=private-ranges-only`.
  ⚠️ Verify region support for `europe-central2` first; **fallback** is the original
  Serverless VPC Access connector (hourly-billed, part of the ~$15–40/mo floor):
  `gcloud compute networks vpc-access connectors create plomien-vpc --region=europe-central2 --range=10.8.0.0/28`.
- [x] Wire Cloud Run → Cloud SQL:
  > **Done 2026-07-04** (rev `plomien-api-00003`, `/api/ping` still 200). Two deviations:
  > (1) Direct VPC egress **requires `--max-instances ≤ 10`** — the service was at 12,
  > first attempt failed; retried with `--max-instances=10` (harmless at this traffic).
  > (2) `--add-cloudsql-instances` skipped — connection is direct to the private IP
  > (`DB_HOST=10.10.0.3`), no unix-socket mount needed.
  ```
  gcloud run services update plomien-api --region=europe-central2 \
    --max-instances=10 \
    --network=default --subnet=default --vpc-egress=private-ranges-only \
    --set-secrets DB_PASSWORD=db-password:latest \
    --update-env-vars DB_HOST=10.10.0.3,DB_NAME=plomien,DB_USER=plomien
  ```
  (swap the `--network/--subnet/--vpc-egress` trio for `--vpc-connector plomien-vpc` if
  the fallback was needed; add `LLM_API_KEY=llm-api-key:latest` to `--set-secrets` when
  the LLM client lands — not part of Phase C.)
  ⚠️ **`--min-instances` stays 0 for Phase C** — the test endpoint is not
  latency-sensitive. Flip to 1 only when the admin LLM-generation flow ships and the JVM
  cold-start NFR ("potwierdzenie bez zauważalnej zwłoki") becomes binding.
- [ ] Add the persistence stack to `backend/pom.xml`: `spring-boot-starter-data-jpa`,
  `org.postgresql:postgresql`, Flyway (`flyway-core` + `flyway-database-postgresql`) as
  the forward-only migration tool. ⚠️ infrastructure.md "Rollback" caveat: a code rollback
  does **not** roll back a schema migration — design migrations additive/backward-compatible
  (binding under Phase 5's auto-deploy: the previous revision keeps serving during rollout
  and must tolerate the new schema).
- [ ] Local dev parity: run PostgreSQL locally (e.g. Docker) with the same `DB_*` env-var
  contract; `application.properties` reads
  `spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:plomien}` etc.,
  so the image is identical across environments.
- [ ] Store the LLM API key and IdP client secret in Secret Manager (same pattern as
  `db-password`); reference the latest Claude model per the project's AI guidance when the
  client is added. **Deferred past Phase C** — the test flow needs only the DB password.

⚠️ **Edge case — storage can't shrink:** Cloud SQL storage auto-grows and cannot be reduced
in place (infrastructure.md). Cap DB log verbosity; if it balloons, dump → restore to a
smaller instance → delete the original.

---

## Phase C (NEW, 2026-07-04) — E2E test data flow: SPA → API → DB 🤖

**Goal / milestone finale:** a test endpoint on the frontend accepts a simple text,
sends it to the backend, and the backend persists it in a test table — confirming the
full data path (browser → Firebase Hosting SPA → CORS → Cloud Run API → Cloud SQL) and,
merged via a PR, exercising the Phase 5 auto-deploy pipeline end-to-end. Requires Phase B
provisioned and Phase 5 operational (this is deliberately the first PR that rides
auto-deploy all the way to production).

Everything here is application code — reviewable in a PR, no direct GCP mutations.

- [ ] **Migration `V1__create_test_messages.sql`** (`backend/src/main/resources/db/migration/`):
  ```sql
  CREATE TABLE test_messages (
    id         BIGSERIAL PRIMARY KEY,
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  ```
  Flyway runs it at app startup. ⚠️ If startup-time migration ever gets slow/risky, split
  migration out of the request path — fine at this scale.
- [ ] **Backend** (`com.plomienkostrze.web`, same package convention as `PingController`):
  `TestMessageController` — `POST /api/test-messages` accepting `{"content": "<text>"}`
  (validate non-blank, cap length e.g. 1 kB), persisting via a Spring Data JPA
  repository/entity, returning `201` with the saved row (`id`, `content`, `createdAt`);
  `GET /api/test-messages` returning the latest N rows (proves read-back without DB
  console access). ⚠️ CORS: `POST` with a JSON body triggers a preflight — already
  handled by `CorsConfig` reading `ALLOWED_ORIGINS`, verify in devtools, not just curl.
- [ ] **Frontend**: a minimal standalone component (signals, per frontend/CLAUDE.md) on a
  test route (e.g. `/test-flow`): one input + submit button POSTing via `HttpClient` to
  `${apiBaseUrl}/api/test-messages`, then re-fetching the GET list and rendering it —
  the round-trip visible in one screen. Mark it clearly as a temporary diagnostic view.
- [ ] **Ship it through the pipeline:** open a PR (build+test checks run), review, merge
  to `master` → both path-filtered workflows auto-deploy (backend image + hosting).
- [ ] 🔒 **Retire the probe later:** drop the route/component and endpoint once real
  features land; the `test_messages` table can stay until the first real migration
  removes it (additive-only rule applies).

---

## Edge cases & extra support (cross-phase reference)

| Area | Trap | Support step |
|---|---|---|
| Angular output | `@angular/build:application` nests under `browser/` | `firebase.json` public = `dist/plomien-kostrze/browser` |
| SPA routing | Deep-link refresh 404s on Firebase | `rewrites` `**` → `/index.html` |
| Cloud Run port | App must honor injected `$PORT` | `-Dserver.port=${PORT:-8080}` |
| Firebase init | Interactive init clobbers committed config | Skip `firebase init`; commit the two files |
| WIF | Any repo could mint tokens | `--attribute-condition` pinning `rkulig/plomienkostrze` |
| Region | `europe-central2` lags `us-central1` on preview features | Check feature/region availability before relying on any non-GA feature; status-date it |
| Cost | Free tier covers requests, not DB/connector/warm instance | Budget alert $25/$50 *before* Phase B |
| Buildpacks billing | `--source` w/o Dockerfile silently bills Cloud Build + AR | Committed `backend/Dockerfile` (chosen) |
| Migrations | Code rollback ≠ schema rollback | Forward-only, additive migrations |

---

## Verification (end-to-end)

1. **Backend up:** `curl https://<cloud-run-url>/api/ping` → `{"status":"ok"}`;
   `gcloud run services logs read plomien-api --region europe-central2 --limit 50` shows a
   clean Spring boot.
2. **Frontend up:** live Firebase URL loads the SPA; a deep link (e.g. `/whatever`) refreshes
   without 404 (proves the rewrite).
3. **Integration:** the SPA makes a real cross-origin call to `/api/ping`; browser Network
   tab shows the preflight `OPTIONS` 200 then the GET 200 (proves CORS + `apiBaseUrl`).
4. **Rollback drill (no-op safe):** `gcloud run revisions list --service plomien-api` shows
   ≥1 retained revision; confirm `update-traffic --to-revisions <PREV>=100` is available.
   `firebase hosting:rollback` is available on the Hosting side.
5. **CI (auto-deploy model):** open a PR with a trivial `backend/**` change → only the
   build+test check runs (no deploy); merge it → the backend workflow builds, deploys,
   and the new revision serves 100% traffic without manual steps; a `context/`-only
   change triggers neither workflow. Same shape for `frontend/**` → live Hosting deploy
   on merge.
6. **Cost guard:** confirm the budget alert exists; after Phase B the floor is the
   always-on `db-f1-micro` (~$7–10/mo) — verify no VPC connector is billing if Direct
   VPC egress was used, and Cloud Run still shows `min-instances=0`.
7. **E2E data flow (Phase C finale):** on the live SPA open `/test-flow`, submit a text,
   and see it come back in the rendered list (id + timestamp) — proving
   browser → Firebase Hosting → CORS preflight → Cloud Run → Cloud SQL write → read-back.
   Cross-check server-side: `gcloud run services logs read plomien-api --region
   europe-central2 --limit 20` shows the POST, and `curl <api>/api/test-messages`
   returns the row.

---

## Out of scope (carried from infrastructure.md)

- Multi-region / HA Cloud SQL / DR / SLA — MVP is single-region `europe-central2`.
- GraalVM native image — the durable cold-start fix, but an architecture milestone (needs
  AOT/reflection hints for the LLM client), not part of this plan.
- Custom domain + TLS for the SPA/API — add after the `.web.app` / `.run.app` URLs are proven.
- Application features themselves (news CRUD, LLM generation, IdP auth) — this plan ships
  the *scaffolds* and the deploy pipeline; Phase B is gated on those features landing.
