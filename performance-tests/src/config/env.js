// Centralised access to k6 environment variables.
//
// Tests should NEVER reach for `__ENV` directly — go through this module so
// defaults, parsing, and the override surface stay in one place.

const required = (name) => {
  const value = __ENV[name];
  if (!value || value.length === 0) {
    throw new Error(`Missing required env var: ${name}`);
  }
  return value;
};

const optional = (name, fallback) => {
  const value = __ENV[name];
  return value && value.length > 0 ? value : fallback;
};

const optionalInt = (name, fallback) => {
  const raw = __ENV[name];
  if (!raw || raw.length === 0) return fallback;
  const parsed = parseInt(raw, 10);
  return Number.isNaN(parsed) ? fallback : parsed;
};

export const env = {
  // Where the API under test lives.
  targetUrl: optional('K6_TARGET_URL', 'http://localhost:8080'),

  // Identifies the calling service in the X-Service-Name header.
  serviceName: optional('K6_SERVICE_NAME', 'CIVIL_DECIDE'),

  // Bearer token. Local dev defaults to the dummy token used by feature.disable-security profiles.
  token: optional('K6_TOKEN', 'swagger-caseworker-token'),

  // Which scenario to run (smoke | load | stress | soak | spike).
  scenario: optional('K6_SCENARIO', 'smoke'),

  // Override knobs — handy for ad-hoc tuning without code edits.
  vusOverride: optionalInt('K6_VUS_OVERRIDE', null),
  durationOverride: optional('K6_DURATION_OVERRIDE', null),

  // Where handleSummary() writes its JSON report.
  summaryOut: optional('K6_SUMMARY_OUT', '/tmp/k6-summary.json'),

  // Where handleSummary() writes its JUnit XML report (CI reporter friendly).
  junitOut: optional('K6_JUNIT_OUT', '/tmp/k6-junit.xml'),

  // Default per-request timeout. Overridable per-call via the http wrapper.
  requestTimeout: optional('K6_REQUEST_TIMEOUT', '10s'),

  // Optional health check URL. If unset, preflight uses targetUrl + /actuator/health.
  healthUrl: optional('K6_HEALTH_URL', null),

  // Token refresh endpoint. If set, auth refreshes the bearer on TTL expiry.
  tokenRefreshUrl: optional('K6_TOKEN_REFRESH_URL', null),
  tokenTtlSeconds: optionalInt('K6_TOKEN_TTL_SECONDS', 3600),
};

export const requireEnv = required;
