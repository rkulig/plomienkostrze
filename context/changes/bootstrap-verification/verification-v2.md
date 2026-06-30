---
bootstrapped_at: 2026-06-30T11:07:25Z
starter_id: spring
starter_name: Spring Boot
project_name: plomien-kostrze-api
language_family: java
package_manager: maven
cwd_strategy: adapted-direct-to-subdir (scaffolded into backend/, not cwd — see Scaffold log)
bootstrapper_confidence: verified
phase_3_status: ok
audit_command: "null"
---

## Hand-off

Verbatim copy of `context/foundation/tech-stack-backend.md`.

```yaml
starter_id: spring
package_manager: maven
project_name: plomien-kostrze-api
hints:
  language_family: java
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

Solo developer building the Płomień Kostrze fan portal as an API-first, decoupled
product. This file covers the backend: a Java / Spring Boot API that the Angular SPA
(see tech-stack.md) consumes today and that future Android/iOS apps will reuse — the
load-bearing, longest-lived artifact, which is why an explicit Java/Spring preference
was honored over the JS full-stack default. Spring Boot clears all four agent-friendly
gates (typed, convention-based, popular within the Java family, well-documented) with
verified bootstrapper confidence. Deployment targets Google Cloud (Cloud Run) as a
containerized service rather than the card's listed defaults of fly/render/aws-ecs,
per the user's GCP requirement; CI runs on GitHub Actions with manual promotion after
merge. Auth (admin login plus external-IdP fan login) and the LLM news-generation
pipeline are in MVP scope; payments, realtime, and background jobs are out per the
PRD. Frontend and backend live together in a single monorepo — the Angular app under
frontend/, this Spring API under backend/ — sharing agents, skills, and the context/
foundation at the repo root while staying decoupled across the HTTP API boundary; the
-api suffix on project_name is only a component identity, not a separate repo.
Timeline is explicitly flexible: the MVP may run past the nominal 3 weeks, accepted
for the long-term mobile-reuse payoff.

## Pre-scaffold verification

| Signal       | Value                                  | Severity | Notes                                                    |
| ------------ | -------------------------------------- | -------- | -------------------------------------------------------- |
| npm package  | not run                                | —        | non-JS starter; cmd_template is `curl … \| tar`          |
| GitHub repo  | not run                                | —        | card docs_url is https://docs.spring.io/spring-boot/ (not GitHub) — no recency signal available |

## Scaffold log

**Resolved invocation**:
```
curl -s https://start.spring.io/starter.tgz \
  -d dependencies=web,devtools -d type=maven-project -d javaVersion=21 \
  -d groupId=com.plomienkostrze -d artifactId=plomien-kostrze-api \
  -d name=plomien-kostrze-api -d packageName=com.plomienkostrze | tar -xzf - -C backend
```
**Strategy**: adapted-direct-to-subdir — the `spring` card is not in `bootstrapper-config.yaml`,
so its default `cwd_strategy` would be `subdir-then-move`; but the start.spring.io tarball
extracts flat (the `{name}` placeholder only sets the Maven artifactId, not a wrapping dir),
so it was extracted straight into `backend/` via `tar -C backend`. The empty subdir meant no
conflict matrix was needed.
**Customizations vs registry template**: `groupId=com.example` → `com.plomienkostrze`
(user choice); added `name` and `packageName` for a clean base package `com.plomienkostrze`.
**Exit code**: 0 (curl 0 / tar 0). The `tar: Ignoring unknown extended header keyword
'LIBARCHIVE.creationtime'` lines are harmless (GNU tar reading a libarchive-produced tgz).
**Files created**: full Maven Spring Boot project under `backend/` — `pom.xml`, `mvnw`,
`mvnw.cmd`, `.mvn/wrapper/`, `src/main/java/com/plomienkostrze/PlomienKostrzeApiApplication.java`,
`src/test/java/com/plomienkostrze/PlomienKostrzeApiApplicationTests.java`,
`src/main/resources/`, `HELP.md`, `.gitignore`, `.gitattributes`.
**Conflicts (.scaffold siblings)**: none (target subdir was empty).
**.gitignore handling**: Spring wrote `backend/.gitignore` (ignores `target/`). Not merged — nested.

### Build verification (extra check, beyond bootstrapper scope)
First `./mvnw package` failed: `release version 21 not supported` — the machine had only a
Java 21 **JRE** (`java`, no `javac`). Resolved by installing `openjdk-21-jdk-headless`
(21.0.11) via apt. Rebuild with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`:
**`./mvnw -q -B -DskipTests package` → exit 0**, artifact
`backend/target/plomien-kostrze-api-0.0.1-SNAPSHOT.jar`. Tests were not run (`-DskipTests`).

## Post-scaffold audit

**Tool**: skipped — no built-in audit tool for `java` (`audit_commands[java]` is `null`).
**Recommended external tool**: OWASP Dependency-Check (Maven plugin) or Snyk. Configure one
separately to scan the dependency tree; neither ships with the JDK/Maven toolchain.

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
Spring Boot backend is scaffolded and build-verified — happy hacking.

Monorepo state & notes:
- **Both components are now scaffolded**: Angular → `frontend/` (see `verification.md`),
  Spring Boot → `backend/` (this log).
- **Java toolchain**: the build needs a JDK, not just the JRE. `openjdk-21-jdk-headless`
  (21.0.11) is now installed. `JAVA_HOME` is unset globally — set it
  (`export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`) in your shell profile so
  `./mvnw` always finds the compiler.
- **Security audit is not wired** for Java — add OWASP Dependency-Check or Snyk if you
  want dependency scanning in CI.
- **No `git init`** — the repo root is already a git repo on `master`; the new `backend/`
  files are tracked by it (`backend/target/` is gitignored). Stage/commit when ready.
- **Group/package**: `com.plomienkostrze` (per user choice); base package and main class
  `com.plomienkostrze.PlomienKostrzeApiApplication`.
