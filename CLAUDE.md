<!-- Project-owned section. Kept OUTSIDE the 10x-cli markers above so a CLI update won't overwrite it. -->

## Project: Płomień Kostrze — monorepo conventions

This repo is a **monorepo** holding two architecturally decoupled applications that
share one set of agents, skills, and foundation context.

### Layout

```
plomienkostrze/            # repo root — shared tooling + context
├── .claude/               # skills + settings (shared; gitignored, managed by 10x-cli)
├── .agents/               # agents (shared; gitignored, managed by 10x-cli)
├── CLAUDE.md              # this file
├── context/foundation/    # prd.md, tech-stack.md (frontend), tech-stack-backend.md
├── frontend/              # Angular SPA — TypeScript; own package.json, angular.json
└── backend/               # Spring Boot API — Java; own pom.xml
```

### Boundary rules

- **Decoupling is the HTTP/REST API contract, not the repo split.** `frontend/` and
  `backend/` share no code; they communicate only over the backend's HTTP API. The
  same API is what future Android/iOS apps will consume.
- **Each app owns its own toolchain.** Angular (npm) under `frontend/`, Spring Boot
  (Maven) under `backend/`. Don't hoist one app's build config to the root.
- **Shared, root-level only:** agents, skills, `context/` foundation, this `CLAUDE.md`.
  Agents/skills are shared by virtue of one working directory (they're gitignored, not
  Git-tracked). Optional per-app `frontend/CLAUDE.md` / `backend/CLAUDE.md` may carry
  app-specific conventions.

### Build, deploy, CI

- **Deploy:** Google Cloud (`europe-central2`) — backend on **Cloud Run** (containerized,
  `backend/Dockerfile`), frontend SPA on **Firebase Hosting**, database on **Cloud SQL
  for PostgreSQL**. Runbook: `context/changes/deployment/deployment-plan.md`.
- **CI:** GitHub Actions with **path-filtered** workflows — the frontend job triggers
  on `frontend/**`, the backend job on `backend/**` — and **automatic deploy on merge
  to `master`** (changed 2026-07-04, was manual promotion; PR review + merge is the
  human gate, PRs run build+test only).

### Stack hand-offs

- `context/foundation/tech-stack.md` — frontend (`angular`), canonical.
- `context/foundation/tech-stack-backend.md` — backend (`spring`).
- Both apps are already scaffolded: Angular in `frontend/`, Spring Boot in `backend/`.
  Toolchains are pinned per app — `frontend/.nvmrc` (Node 24.18.0), `backend/.sdkmanrc`
  (Java 21); the backend build needs a JDK, not just a JRE. Bootstrap verification logs
  live in `context/changes/bootstrap-verification/` (`verification.md` frontend,
  `verification-v2.md` backend).

<!-- BEGIN @przeprogramowani/10x-cli -->

## 10xDevs AI Toolkit - Module 3, Lesson 4 (E2E Tests)

**For E2E tests, use the `/10x-e2e` skill.** It is the single source of truth
for the workflow — risk → seed test + rules → generate → review against the five
anti-patterns → re-prompt → verify. The skill's `references/` carry the full
rules, anti-patterns, seed pattern, and prompt-template.

A few hard rules that hold even before you invoke the skill:

- **Locators:** `getByRole` / `getByLabel` / `getByText` first; `getByTestId`
  only when accessibility attributes are ambiguous. Never CSS selectors, XPath,
  or DOM structure.
- **Never `page.waitForTimeout()`.** Wait for state: `toBeVisible()`,
  `waitForURL()`, `waitForResponse()`.
- **Test independence + cleanup.** Each test runs standalone — its own setup,
  action, assertion, and cleanup; unique ids (timestamp suffix) so parallel runs
  and re-runs don't collide.

Two boundaries to keep straight:

- **DOM (snapshot) is the default.** Vision (`--caps=vision`) is a supplement for
  visual-only risks (layout, z-index, animation); for pixel regression prefer
  deterministic tools (`toMatchSnapshot`, Argos, Lost Pixel). VLM model
  selection/cost is a debugging topic (Lesson 5), not testing.
- **Healer helps on selectors, harms on logic.** A changed selector → healer
  re-finds it (route through PR review). A changed business behavior → healer
  masks the bug; that failing-test-to-fix case is Lesson 5.

<!-- END @przeprogramowani/10x-cli -->
