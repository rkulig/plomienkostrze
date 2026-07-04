---
starter_id: angular
package_manager: npm
project_name: plomien-kostrze
hints:
  language_family: js
  team_size: solo
  deployment_target: firebase-hosting
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge  # changed 2026-07-04, was: manual-promotion
  identity_provider: firebase-auth  # decided 2026-07-04
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
---

## Why this stack

Solo developer building the Płomień Kostrze fan portal as a deliberately decoupled,
API-first product so future Android/iOS apps can reuse the same backend. This file
covers the frontend: an Angular (TypeScript) SPA that consumes the separate Spring
Boot API (see tech-stack-backend.md). Angular was chosen over React on explicit
preference; it clears all four agent-friendly gates (typed, convention-based,
popular within the JS family, well-documented) and its bootstrapper confidence is
verified, so scaffolding will be smooth. Deployment targets Google Cloud per the
user's GCP requirement (decided in `context/foundation/infrastructure.md`): the static
Angular SPA ships to **Firebase Hosting** — GCP-native static hosting with a global CDN
and a one-command `firebase deploy` — rather than an nginx container on Cloud Run, which
would cost cold starts and the free CDN for no gain. The SPA calls the separate Spring
Boot API on Cloud Run (`europe-central2`) over HTTPS with CORS configured to the SPA
origin. CI runs on GitHub Actions with automatic deploy on merge to master (changed
2026-07-04 from manual promotion — PR review + merge is the human gate).
Auth is delegated to
**Firebase Authentication** (decided 2026-07-04): the SPA integrates the Firebase Auth
SDK — Google sign-in now, Facebook in the fan-login fast-follow — and sends the Firebase
ID token in the Authorization header on calls to the Cloud Run API; admin login is in MVP
scope, and the AI news-generation flow is in MVP scope as well; payments, realtime, and
background jobs are out per the PRD.
Frontend and backend live together in a single monorepo — this Angular app under
frontend/, the Spring API under backend/ — sharing agents, skills, and the context/
foundation at the repo root while staying decoupled across the HTTP API boundary.
Timeline is explicitly flexible: the MVP may run past the nominal 3 weeks, traded
for the long-term mobile-reuse payoff of the decoupled API.
