export const environment = {
  production: true,
  // Cloud Run service URL (deployed 2026-07-02).
  apiBaseUrl: 'https://plomien-api-714793368062.europe-central2.run.app',
  // Default authDomain: sign-in uses a popup (postMessage), so same-origin
  // with hosting is not required; the web.app redirect URI never propagated
  // to the Google OAuth client (persistent redirect_uri_mismatch).
  firebase: {
    apiKey: 'AIzaSyAjHmuT_ve5veMZzyCwWO0HDTYxm4SFINQ',
    authDomain: 'plomien-kostrze.firebaseapp.com',
    projectId: 'plomien-kostrze',
  },
};
