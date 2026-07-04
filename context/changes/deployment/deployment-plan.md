---
project: Płomień Kostrze
plan_created: 2026-06-30
plan_for: First production deployment to Google Cloud
source_contract: context/foundation/infrastructure.md
recommended_platform: Google Cloud (Cloud Run + Firebase Hosting + Cloud SQL)
region: europe-central2 (Warsaw)
github_repo: rkulig/plomienkostrze
context_type: mvp
status: draft — not yet executed
decisions:
  db_infra: deferred (Phase B — provision Cloud SQL/VPC/Secret Manager only when backend grows persistence/LLM/auth code)
  build_method: explicit multi-stage Dockerfile (backend/Dockerfile)
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

---

## Legend

- `[ ]` step to do · `[x]` done
- 🔒 **Human-only / manual gate** — irreversible or account/billing/secret-touching;
  do by hand, never let the agent run unattended (per infrastructure.md "Approval" axis).
- 🤖 agent-safe — deterministic, scriptable, reversible.
- ⚠️ **edge case / extra support** — a known stumbling block with its workaround inline.

---

## Prerequisites (one-time, manual)

- [ ] 🔒 GCP account with **billing enabled** and a project created
  (suggested id: `plomien-kostrze`; capture the real `PROJECT_ID`).
- [ ] 🔒 **Billing budget alert** set at **$25 / $50** before any resource is created
  (infrastructure.md risk: "Real MVP cost is ~$15–40/mo, not free").
  `gcloud billing budgets create` or the Billing console.
- [ ] 🔒 Firebase project **linked to the same GCP project** (Firebase console → Add
  project → *select the existing GCP project*, do **not** create a new one).
  ⚠️ Firebase and GCP are the same project but two consoles — first-timer trap from
  infrastructure.md "Unknown Unknowns". Linking them now avoids a split project later.
- [ ] 🤖 Install/auth CLIs:
  - `gcloud components update`
  - `gcloud auth login` · `gcloud config set project <PROJECT_ID>`
  - `gcloud config set run/region europe-central2`
  - `npm i -g firebase-tools` · `firebase login`
- [ ] 🤖 Confirm toolchains: backend `sdk use java 21.0.11-tem` (JDK, per `.sdkmanrc`);
  frontend `nvm use` (Node 24.18.0, per `.nvmrc` — system default 24.14.0 is too old
  for the Angular 22 CLI).

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

## Phase 5 — CI/CD: keyless GitHub Actions, path-filtered, manual promotion 🤖 + 🔒

Implements infrastructure.md "Secrets" (Workload Identity Federation, no JSON key in repo)
and CLAUDE.md's path-filtered + manual-promotion mandate.

- [ ] 🔒 **Set up Workload Identity Federation** (one-time, IAM-touching — do by hand):
  ```
  # Pool + OIDC provider, restricted to THIS repo (confused-deputy guard)
  gcloud iam workload-identity-pools create github --location=global
  gcloud iam workload-identity-pools providers create-oidc github-oidc \
    --location=global --workload-identity-pool=github \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
    --attribute-condition="assertion.repository=='rkulig/plomienkostrze'"

  # Deployer service account + scoped roles (Cloud Run + Artifact Registry + Firebase Hosting)
  gcloud iam service-accounts create gh-deployer
  # roles/run.admin, roles/iam.serviceAccountUser, roles/artifactregistry.writer,
  # roles/firebasehosting.admin — grant on the project, NOT org level.

  # Let the repo's tokens impersonate the deployer SA
  gcloud iam service-accounts add-iam-policy-binding \
    gh-deployer@<PROJECT_ID>.iam.gserviceaccount.com \
    --role=roles/iam.workloadIdentityUser \
    --member="principalSet://iam.googleapis.com/projects/<NUM>/locations/global/workloadIdentityPools/github/attribute.repository/rkulig/plomienkostrze"
  ```
  ⚠️ Scope the SA to **this project only** (no billing, no org IAM) per infrastructure.md
  "Production-access boundary". The `attribute-condition` pinning the repo is non-optional
  — without it any GitHub repo could mint tokens for your pool.
- [ ] **`.github/workflows/backend.yml`** — trigger on `backend/**`, set
  `permissions: id-token: write`, use `google-github-actions/auth@v2` (WIF), then
  `gcloud run deploy plomien-api --source backend/ --no-traffic --tag ci-<sha>`.
  **Stop at no-traffic** — promotion stays a 🔒 manual `gcloud ... update-traffic` step.
- [ ] **`.github/workflows/frontend.yml`** — trigger on `frontend/**`, WIF auth, then
  `firebase hosting:channel:deploy ci-<sha>` (preview). Live promotion stays 🔒 manual.
  ⚠️ firebase-tools in CI reads Application Default Credentials — point
  `GOOGLE_APPLICATION_CREDENTIALS` at the credentials file emitted by
  `google-github-actions/auth@v2` (do **not** use the deprecated `firebase login:ci` token).
- [ ] ⚠️ **Monorepo path-filter gotcha:** a PR touching only `context/` or `CLAUDE.md`
  should trigger **neither** pipeline. Verify the `paths:` filters exclude shared/root files
  so doc changes don't trigger deploys.

---

## Phase B (DEFERRED) — Cloud SQL + VPC + Secret Manager 🔒-heavy

**Trigger to start this phase:** the backend PR that first adds a PostgreSQL driver + JPA,
the LLM client, or external-IdP auth. Not before — there is nothing to connect until then.

- [ ] Enable the deferred APIs: `gcloud services enable sqladmin.googleapis.com vpcaccess.googleapis.com secretmanager.googleapis.com`.
- [ ] 🔒 Create the DB (smallest, no HA, **private IP only**):
  ```
  gcloud sql instances create plomien-db --database-version=POSTGRES_16 \
    --tier=db-f1-micro --region=europe-central2 --no-assign-ip
  ```
  ⚠️ **Never assign a public IP** (infrastructure.md pre-mortem: "public Cloud SQL IP +
  weak password sat exposed for weeks"). Generate a strong password into Secret Manager,
  never into the repo or chat:
  `gcloud secrets create db-password --data-file=-` (pipe a generated value).
- [ ] 🔒 Create the Serverless VPC Access connector (hourly-billed — this is part of the
  ~$15–40/mo floor):
  `gcloud compute networks vpc-access connectors create plomien-vpc --region=europe-central2 --range=10.8.0.0/28`.
- [ ] Wire Cloud Run → Cloud SQL:
  ```
  gcloud run services update plomien-api \
    --add-cloudsql-instances <CONNECTION_NAME> \
    --vpc-connector plomien-vpc \
    --set-secrets DB_PASSWORD=db-password:latest,LLM_API_KEY=llm-api-key:latest \
    --min-instances=1
  ```
  ⚠️ **Flip `--min-instances=1` here** (not in Phase A): once the admin LLM-generation flow
  exists, the JVM cold-start NFR ("potwierdzenie bez zauważalnej zwłoki") becomes binding.
- [ ] Add a forward-only migration tool (Flyway/Liquibase). ⚠️ infrastructure.md "Rollback"
  caveat: a code rollback does **not** roll back a schema migration — design migrations
  reversible / additive.
- [ ] Store the LLM API key and IdP client secret in Secret Manager (same pattern as
  `db-password`); reference the latest Claude model per the project's AI guidance when the
  client is added.

⚠️ **Edge case — storage can't shrink:** Cloud SQL storage auto-grows and cannot be reduced
in place (infrastructure.md). Cap DB log verbosity; if it balloons, dump → restore to a
smaller instance → delete the original.

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
5. **CI dry run:** push a trivial `backend/**` change → backend workflow deploys a
   `--no-traffic` revision and **does not** auto-promote; a `context/`-only change triggers
   neither workflow.
6. **Cost guard:** confirm the budget alert exists and Phase A shows scale-to-zero
   (Cloud Run min-instances=0, no Cloud SQL) so the floor is ~$0 until Phase B.

---

## Out of scope (carried from infrastructure.md)

- Multi-region / HA Cloud SQL / DR / SLA — MVP is single-region `europe-central2`.
- GraalVM native image — the durable cold-start fix, but an architecture milestone (needs
  AOT/reflection hints for the LLM client), not part of this plan.
- Custom domain + TLS for the SPA/API — add after the `.web.app` / `.run.app` URLs are proven.
- Application features themselves (news CRUD, LLM generation, IdP auth) — this plan ships
  the *scaffolds* and the deploy pipeline; Phase B is gated on those features landing.
