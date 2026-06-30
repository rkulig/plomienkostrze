---
bootstrapped_at: 2026-06-30T10:45:58Z
starter_id: angular
starter_name: Angular
project_name: plomien-kostrze
language_family: js
package_manager: npm
cwd_strategy: adapted-direct-to-subdir (scaffolded into frontend/, not cwd — see Scaffold log)
bootstrapper_confidence: verified
phase_3_status: ok
audit_command: npm audit --json
---

## Hand-off

Verbatim copy of `context/foundation/tech-stack.md`.

```yaml
starter_id: angular
package_manager: npm
project_name: plomien-kostrze
hints:
  language_family: js
  team_size: solo
  deployment_target: google-cloud-run
  ci_provider: github-actions
  ci_default_flow: manual-promotion
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
```

### Why this stack

Solo developer building the Płomień Kostrze fan portal as a deliberately decoupled,
API-first product so future Android/iOS apps can reuse the same backend. This file
covers the frontend: an Angular (TypeScript) SPA that consumes the separate Spring
Boot API (see tech-stack-backend.md). Angular was chosen over React on explicit
preference; it clears all four agent-friendly gates (typed, convention-based,
popular within the JS family, well-documented) and its bootstrapper confidence is
verified, so scaffolding will be smooth. Deployment targets Google Cloud (Cloud
Run) rather than the card's listed defaults, per the user's GCP requirement; CI runs
on GitHub Actions with manual promotion after merge to gate live pushes. Auth (admin
login, plus fan login via an external identity provider) and the AI news-generation
flow are in MVP scope; payments, realtime, and background jobs are out per the PRD.
Frontend and backend live together in a single monorepo — this Angular app under
frontend/, the Spring API under backend/ — sharing agents, skills, and the context/
foundation at the repo root while staying decoupled across the HTTP API boundary.
Timeline is explicitly flexible: the MVP may run past the nominal 3 weeks, traded
for the long-term mobile-reuse payoff of the decoupled API.

## Pre-scaffold verification

| Signal       | Value                                                | Severity | Notes                                            |
| ------------ | ---------------------------------------------------- | -------- | ------------------------------------------------ |
| npm package  | @angular/cli v22.0.4 published 2026-06-24            | fresh    | resolved from cmd_template; <1 week old          |
| GitHub repo  | not run                                              | —        | card docs_url is https://angular.dev (not GitHub) |

## Scaffold log

**Resolved invocation**: `npx --yes @angular/cli@latest new plomien-kostrze --directory frontend --defaults --routing --style scss --skip-tests --ssr false --skip-git`
**Strategy**: adapted-direct-to-subdir — the registry card's `cwd_strategy` default
(`subdir-then-move`) would have landed Angular at the repo root; for this monorepo the
CLI was pointed at `frontend/` directly via `--directory` (the subdir was empty, so no
conflict matrix was needed). `--skip-git` prevents a nested repo inside `frontend/`.
**Exit code**: 0
**Toolchain note**: first attempt failed (exit 3) — Node v24.14.0 was below Angular CLI
22's required `^24.15.0`. Resolved by installing Node v24.18.0 via nvm, then re-running.
**Files created**: full Angular workspace under `frontend/` (angular.json, package.json,
tsconfig*.json, src/app/* with routing, public/, .vscode/, .prettierrc, frontend/.gitignore).
Dependencies installed by `ng new` (no `--skip-install`).
**Conflicts (.scaffold siblings)**: none (target subdir was empty).
**.gitignore handling**: Angular wrote `frontend/.gitignore` (covers .angular/, dist/,
node_modules/). Root `.gitignore` already ignores `node_modules/`. Not merged — nested.
**.bootstrap-scaffold cleanup**: n/a (temp-dir strategy not used).

## Post-scaffold audit

**Tool**: `npm audit --json` (run in `frontend/`)
**Summary**: 0 CRITICAL, 0 HIGH, 0 MODERATE, 3 LOW
**Direct vs transitive**: 1 LOW direct (`@angular/build`) of 3 LOW total; 2 LOW transitive.
**Dependencies scanned**: prod 11, dev 501, optional 135 (total 511).
All findings are in the dev/build toolchain (esbuild and dependents); none affect runtime.

#### CRITICAL findings

None.

#### HIGH findings

None.

#### MODERATE findings

None.

#### LOW / INFO findings

- **@angular/build** (direct, low) — affected `<=20.3.29 || 21.0.0-next.0 - 21.2.16 || 22.0.0-next.0 - 22.0.4`, via `@babel/core`. Fix available: `@angular/build@21.2.17` (flagged semver-major by npm; a downgrade from the installed 22.x line — review before applying).
- **@babel/core** (transitive, low) — "Arbitrary File Read via sourceMappingURL Comment". Pulled in via `@angular/build`.
- **esbuild** (transitive, low) — "esbuild allows arbitrary file read when running the development server on Windows" (range `0.27.3 - 0.28.0`). Dev-server only; Windows-specific.

These are low-severity, build-time advisories typical of a freshly scaffolded Angular
workspace. No action required for day one; revisit when Angular ships a patch release.
`npm audit fix` was NOT run (bootstrapper informs; the user decides).

## Hints recorded but not acted on

v1 surfaces these but takes no automated action.

| Hint                    | Value                                                  |
| ----------------------- | ------------------------------------------------------ |
| bootstrapper_confidence | verified                                               |
| quality_override        | false                                                  |
| path_taken              | custom                                                 |
| team_size               | solo                                                   |
| deployment_target       | google-cloud-run                                       |
| ci_provider             | github-actions                                         |
| ci_default_flow         | manual-promotion                                       |
| self_check_answers      | typed:true, from_official_starter:true, conventions:true, docs_current:true, can_judge_agent:true |
| has_auth                | true                                                   |
| has_payments            | false                                                  |
| has_realtime            | false                                                  |
| has_ai                  | true                                                   |
| has_background_jobs     | false                                                  |

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, the
Angular frontend is scaffolded and verified — happy hacking.

Monorepo-specific notes:
- **Backend is not yet scaffolded.** Run the bootstrap again against
  `context/foundation/tech-stack-backend.md` (Spring Boot → `backend/`). Java has no
  built-in audit command, so that run's audit step will be skipped; the `spring`
  `curl … | tar -xzf -` template extracts flat, so target it with `tar -C backend`.
- **No `git init` needed** — the repo root (`/home/tom/plomienkostrze`) is already a git
  repo on branch `master`. The new `frontend/` files are tracked by the existing repo;
  `--skip-git` only prevented a nested repo inside `frontend/`. `node_modules/` is already
  covered by the root `.gitignore`. Stage/commit the new files when ready.
- **Node version**: Angular CLI 22 requires Node ≥ v24.15.0; this machine now has
  v24.18.0 installed via nvm. Consider adding an `frontend/.nvmrc` (or root) pinning the
  Node version so the toolchain is reproducible.
- Review the LOW audit findings above per your risk tolerance — the full breakdown is in
  this log.
