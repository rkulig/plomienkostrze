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

## 10xDevs AI Toolkit - Module 2, Lesson 1

Move from sprint-zero setup to project orchestration with the **roadmap chain**:

```
(Module 1 foundation docs) -> /10x-roadmap -> backlog-ready roadmap items
```

`/10x-roadmap` is the lesson focus. `/10x-new` is intentionally introduced in Module 2, Lesson 2, when a selected roadmap item becomes an implementation change folder.

### Task Router - Where to start

| Skill | Use it when |
| --- | --- |
| **Roadmap (lesson focus)** | |
| `/10x-roadmap` | You have `context/foundation/prd.md` and a scaffolded project baseline, and you need a vertical-first MVP roadmap. The skill reads the PRD, inspects the code baseline, uses available foundation docs such as `tech-stack.md`, `infrastructure.md`, and `deploy-plan.md`, then writes `context/foundation/roadmap.md`. Use it BEFORE creating per-change folders or implementation plans. |
| **Re-run upstream if needed** | |
| `/10x-shape` / `/10x-prd` / `/10x-tech-stack-selector` / `/10x-bootstrapper` / `/10x-agents-md` / `/10x-infra-research` | Bundled from Module 1 so foundation contracts can be fixed before roadmap sequencing. If roadmap generation exposes a PRD gap, repair the PRD before pretending the backlog is ready. |

### How the chain hands off

- `/10x-roadmap` bridges product and implementation. It does not choose frameworks, design schemas, or write a per-change implementation plan.
- The output is `context/foundation/roadmap.md`: ordered milestones, vertical slices, bounded foundations, dependencies, unknowns, risk, and backlog handoff fields.
- Roadmap items should receive stable human-readable identifiers in backlog tools. The actual `context/changes/<change-id>/` folder is created in Lesson 2 with `/10x-new`.

### Roadmap boundaries

- Default to vertical slices: user-visible outcomes that cross UI, data, business logic, and integrations.
- Horizontal work is allowed only as a bounded enabler that names the downstream vertical milestone it unlocks.
- Avoid orphan horizontal work such as "build the whole database", "build all API endpoints", or "design the whole UI" before the first user-visible flow.
- Roadmap is not a calendar estimate. Do not invent dates, story points, or sprint velocity unless the user explicitly asks for a separate planning artifact.

### Foundation paths used by this lesson

- `context/foundation/prd.md` - input
- `context/foundation/tech-stack.md` - optional input
- `context/foundation/infrastructure.md` - optional input
- `context/deployment/deploy-plan.md` - optional input
- `context/foundation/roadmap.md` - output
- `context/foundation/lessons.md` - recurring rules and pitfalls
- `docs/reference/contract-surfaces.md` - load-bearing names registry

Skills must not write to `context/archive/`. Archived changes are immutable; if a resolved target path starts with `context/archive/`, abort with: "This change is archived. Open a new change with `/10x-new` instead."

<!-- END @przeprogramowani/10x-cli -->
