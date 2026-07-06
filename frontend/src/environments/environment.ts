export const environment = {
  production: false,
  // Local Spring Boot API (see backend/ — runs on :8080).
  apiBaseUrl: 'http://localhost:8080',
  // Firebase web app config (public by design — not a secret). Dev uses the
  // default authDomain; prod overrides it to web.app for same-origin redirect.
  firebase: {
    apiKey: 'AIzaSyAjHmuT_ve5veMZzyCwWO0HDTYxm4SFINQ',
    authDomain: 'plomien-kostrze.firebaseapp.com',
    projectId: 'plomien-kostrze',
  },
};
