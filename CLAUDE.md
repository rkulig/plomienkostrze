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

## 10xDevs AI Toolkit - Module 2, Lesson 5

Scale the single-change cycle into parallel work with **worktrees, goal-directed delegation, and multi-session orchestration**:

```
worktree per change -> /goal or claude -p -> PR -> review -> merge
```

The lesson focus is safe throughput: isolated contexts, choosing the right execution mode, and capping parallelism at review capacity.

### Task Router - Where to start

| Skill | Use it when |
| --- | --- |
| **Code isolation** | |
| `git worktree add` | You need a separate working directory for a parallel change. One change per worktree, one fresh agent context per worktree. |
| **Complex changes** | |
| `/10x-implement <change-id> phase <n>` | The change has multiple phases, needs manual gates, or benefits from interactive decision-making during execution. |
| **Simple changes** | |
| `/goal` | You have a clear, bounded task and want goal-directed delegation. The agent works autonomously toward the stated goal with a stop condition. |
| `claude -p` | You want headless execution for a well-defined task. The Ralph Wiggum loop (run, check, retry) is the universal autonomous pattern. |
| **Multi-session orchestration** | |
| Superset / Conductor / Antigravity / VS Code Agent View | You are running multiple agent sessions in parallel and need visibility, coordination, or session management across them. |

### Parallel work rules

- One change per worktree or isolated workspace. One fresh agent context per change.
- Choose interactive `/10x-implement` for complex changes, `/goal` or `claude -p` for simple ones.
- Parallelism is capped by review capacity. More agents without review means more unreviewed code, not higher throughput.
- The quality pain from faster shipping is intentional — it bridges into Module 3 testing gates.

### Lesson boundaries

- Do not reteach interactive `/10x-implement` or `/10x-impl-review`; those are Lessons 2 and 3.
- Do not introduce testing strategy here. The quality pain is the motivation for Module 3.
- Worktrees are a mechanism for isolation, not the topic of a full git tutorial.

### Paths used by this lesson

- `context/changes/<change-id>/` - active change folder
- `context/changes/<change-id>/plan.md` - implementation input for any execution mode

Skills must not write to `context/archive/`. Archived changes are immutable; if a resolved target path starts with `context/archive/`, abort with: "This change is archived. Open a new change with `/10x-new` instead."

<!-- END @przeprogramowani/10x-cli -->
