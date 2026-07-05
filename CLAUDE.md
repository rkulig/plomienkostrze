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

## 10xDevs AI Toolkit - Module 2, Lesson 2

Turn one roadmap item into the first implementation cycle with the **change planning chain**:

```
/10x-roadmap -> /10x-new -> /10x-plan -> /10x-plan-review -> /10x-implement
```

`/10x-new`, `/10x-plan`, `/10x-plan-review`, and `/10x-implement` are the lesson focus. `/10x-frame` and `/10x-research` are not required rituals here; they are escalation paths introduced in the next lesson.

### Task Router - Where to start

| Skill | Use it when |
| --- | --- |
| **Change setup (lesson focus)** | |
| `/10x-new <change-id>` | You selected a roadmap item and need a stable change folder. Creates `context/changes/<change-id>/change.md` so planning, implementation, progress, commits, and later review all share one identity. Use AFTER roadmap selection, BEFORE `/10x-plan`. |
| **Planning (lesson focus)** | |
| `/10x-plan <change-id>` | You have a change folder and need a reviewable implementation plan. Reads roadmap context, foundation docs, codebase evidence, and any existing change notes; writes `plan.md` and `plan-brief.md` with phases, file contracts, success criteria, and `## Progress`. |
| **Plan readiness (lesson focus)** | |
| `/10x-plan-review <change-id>` | You have `plan.md` and need a light pre-code readiness check. Use it to catch missing end state, weak contracts, malformed progress, scope drift, or blind spots before code changes begin. |
| **Implementation (lesson focus)** | |
| `/10x-implement <change-id> phase <n>` | You have an approved plan and want to execute one phase with verification, manual gate, commit ritual, and SHA write-back to `## Progress`. |
| **Lifecycle closure** | |
| `/10x-archive <change-id>` | A change is merged or intentionally closed. Move it out of active `context/changes/` into archive state. |

### How the chain hands off

- `/10x-new` creates the durable change identity.
- `/10x-plan` turns that identity into an implementation contract.
- `/10x-plan-review` checks the plan before the agent mutates code.
- `/10x-implement` executes one planned phase, verifies, asks for manual confirmation when needed, commits, and records progress.

### Lesson boundaries

- Plan is the default router after roadmap selection. Start with `/10x-plan` unless the problem is unclear or external evidence is blocking.
- Do not run `/10x-frame + /10x-research` as ceremony for every change.
- Do not turn this lesson into a full end-to-end product build. A checkpoint with a planned and partially or fully implemented stream is valid.
- Code review of the implemented diff belongs to Lesson 3 via `/10x-impl-review`.
- Lifecycle closure via `/10x-archive` after a change is merged or intentionally closed.

### Paths used by this lesson

- `context/foundation/roadmap.md` - upstream roadmap
- `context/changes/<change-id>/change.md` - change identity
- `context/changes/<change-id>/plan.md` - implementation contract
- `context/changes/<change-id>/plan-brief.md` - compressed handoff
- `context/foundation/lessons.md` - recurring rules and pitfalls
- `docs/reference/contract-surfaces.md` - load-bearing names registry

Skills must not write to `context/archive/`. Archived changes are immutable; if a resolved target path starts with `context/archive/`, abort with: "This change is archived. Open a new change with `/10x-new` instead."

<!-- END @przeprogramowani/10x-cli -->
