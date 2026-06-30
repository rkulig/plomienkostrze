---
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
---

## Why this stack

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
