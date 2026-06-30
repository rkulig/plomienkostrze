# Backend — Spring Boot API (`plomien-kostrze-api`)

Repo-wide monorepo rules live in `../CLAUDE.md` (Claude Code loads it automatically). This file covers only the backend.

## Before you build anything

- **A JDK is required, not just a JRE.** The build needs `javac` (Java 21). If you see `release version 21 not supported`, only the JRE is on PATH. Set `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (pinned in `@backend/.sdkmanrc`).
- **Use the Maven wrapper `./mvnw`** — there is no system `mvn` installed.

## Commands (from `backend/`)

- `./mvnw spring-boot:run` → runs the API on http://localhost:8080
- `./mvnw -DskipTests package` → fat jar in `target/` (gitignored)
- `./mvnw test` → run tests

## Conventions

- **Base package `com.plomienkostrze`.** New code lives under it; main class is `PlomienKostrzeApiApplication` (`@backend/src/main/java/com/plomienkostrze/PlomienKostrzeApiApplication.java`).
- **API-first, no UI.** Dependencies are `web` (Spring MVC) + `devtools` only (`@backend/pom.xml`). This service exposes HTTP for the Angular SPA and future mobile clients — don't add server-side templating/views or couple it to `frontend/`.
- Config goes in `@backend/src/main/resources/application.properties`.

## Tripwires

- Don't commit `target/` (already in `@backend/.gitignore`).
- Fresh project has **no controllers yet** — `GET /` returns a 404 (Spring default). That proves the server booted; add a controller to expose a real endpoint.
