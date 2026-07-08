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

## 10xDevs AI Toolkit - Module 2, Lesson 4

Prepare for a harder implementation stream with the **research-backed planning chain**:

```
internal research (/10x-research) + external research (exa.ai, Context7) -> /10x-plan -> /10x-implement -> success
```

The lesson focus is distinguishing internal from external research and using evidence to back planning decisions.

### Task Router - Where to start

| Skill | Use it when |
| --- | --- |
| **Internal research (lesson focus)** | |
| `/10x-research <change-id>` | You need evidence from the existing codebase — patterns, conventions, integration points, or existing implementations. Runs parallel sub-agents over the repo and writes structured findings to `research.md`. |
| **External research (lesson focus)** | |
| exa.ai | You need AI-native web search for library comparisons, best practices, or ecosystem context that the codebase cannot answer. |
| Context7 (`resolve-library-id` → `get-library-docs`) | You need live, current documentation for a specific library or framework. Resolves a library ID first, then fetches relevant doc pages. |
| **Framing spare wheel** | |
| `/10x-frame <change-id>` | The plan won't converge, the plan doesn't deliver expected results, or persistent drift keeps breaking the implementation. Use as an escape hatch on a separate problem (demonstrated on Space Explorers example), not as pre-research ritual. |
| **Planning and execution** | |
| `/10x-plan <change-id>` / `/10x-implement <change-id> phase <n>` | Use the same planning and execution chain from Lesson 2, now with upstream research evidence feeding the plan. |

### Research discipline

- Internal research (`/10x-research`) answers "what does our codebase already do?" — patterns, schemas, conventions, integration points.
- External research (exa.ai, Context7) answers "what should we do?" — library capabilities, API docs, ecosystem best practices.
- Combine both as evidence-backed input to `/10x-plan`. A plan without research evidence on a non-trivial stream is a guess.
- Agent-friendly docs (`llms.txt`, markdown-for-agents, `/md` endpoints) are a quality signal for library selection — libraries that publish agent-readable docs integrate faster.

### `/10x-frame` as spare wheel

Three triggers for reaching for `/10x-frame`:
1. The plan won't converge — research keeps opening more questions instead of narrowing to a contract.
2. The plan doesn't deliver — implementation repeatedly fails to meet success criteria.
3. Persistent drift — the implementation keeps diverging from the plan in ways that suggest the problem was mis-framed.

Demonstrated on a Space Explorers example, not the SRS path. It is an escape hatch, not a mandatory step.

### Paths used by this lesson

- `context/changes/<change-id>/research.md` - internal research output
- `context/changes/<change-id>/frame.md` - framing output when needed
- `context/changes/<change-id>/plan.md` - evidence-backed implementation contract
- `context/foundation/lessons.md` - recurring rules and pitfalls

Skills must not write to `context/archive/`. Archived changes are immutable; if a resolved target path starts with `context/archive/`, abort with: "This change is archived. Open a new change with `/10x-new` instead."

<!-- END @przeprogramowani/10x-cli -->
