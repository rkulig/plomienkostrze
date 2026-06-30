# Frontend — Angular SPA (`plomien-kostrze`)

Repo-wide monorepo rules live in `../CLAUDE.md` (Claude Code loads it automatically). This file covers only the frontend.

## Before you run anything

- **Run `nvm use` first.** Node is pinned in `@frontend/.nvmrc` (24.18.0). The system default is Node 24.14.0, which Angular 22 CLI **refuses** (`requires ≥ v24.15.0`) — every `ng`/`npm` command fails until you switch.

## Commands (from `frontend/`)

- `npm start` → `ng serve` on http://localhost:4200
- `npm run build` → production build into `dist/` (gitignored)
- `npm test` → `ng test`
- Scripts are in `@frontend/package.json` — use those, don't invent flags.

## Conventions

- **Standalone + signals**, no NgModules. Follow the scaffold pattern: components configured via `@frontend/src/app/app.config.ts` (provider functions), state via `signal()` as in `@frontend/src/app/app.ts`. Routes go in `@frontend/src/app/app.routes.ts`.
- **Styling is SCSS** (`styleUrl`/`styles.scss`), set in `angular.json`. Don't introduce CSS-in-JS or another styling system.
- **Decoupled from the backend.** The SPA talks to the Spring API only over HTTP (the same API future mobile apps reuse). Never import from `backend/`; there is no shared code.

## Tripwires

- Don't commit `dist/` or `.angular/` (already in `@frontend/.gitignore`).
- The backend serves on `:8080`; a bare `GET /` there returns 404 until controllers exist — that's expected, not a bug.
