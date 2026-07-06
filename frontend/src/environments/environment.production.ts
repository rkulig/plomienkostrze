export const environment = {
  production: true,
  // Cloud Run service URL (deployed 2026-07-02).
  apiBaseUrl: 'https://plomien-api-714793368062.europe-central2.run.app',
  // authDomain = hosting domain (web.app) so the sign-in redirect is
  // same-origin — canonical prod URL is plomien-kostrze.web.app.
  firebase: {
    apiKey: 'AIzaSyAjHmuT_ve5veMZzyCwWO0HDTYxm4SFINQ',
    authDomain: 'plomien-kostrze.web.app',
    projectId: 'plomien-kostrze',
  },
};
