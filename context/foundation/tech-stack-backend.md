---
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
---

## Why this stack

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
