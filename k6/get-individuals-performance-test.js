import http from 'k6/http';
import { sleep } from 'k6';

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = parseInt(__ENV.VUS || '5', 10);
const ITERATIONS = parseInt(__ENV.ITERATIONS || '100', 10);
const THINK_TIME_MIN_MS = parseInt(__ENV.THINK_TIME_MIN_MS || '0', 10);
const THINK_TIME_MAX_MS = parseInt(__ENV.THINK_TIME_MAX_MS || '0', 10);
const APPLICATIONS_PAGE_SIZE = parseInt(__ENV.APPLICATIONS_PAGE_SIZE || '20', 10);

// Optional application search filters.
const CLIENT_FIRST_NAME = __ENV.CLIENT_FIRST_NAME || '';
const CLIENT_LAST_NAME = __ENV.CLIENT_LAST_NAME || '';
const CLIENT_DATE_OF_BIRTH = __ENV.CLIENT_DATE_OF_BIRTH || '';
const STATUS = __ENV.STATUS || '';

// individuals endpoint options.
const INCLUDE = __ENV.INCLUDE || 'CLIENT_DETAILS';
const INDIVIDUAL_TYPE = __ENV.INDIVIDUAL_TYPE || 'CLIENT';

// ---------------------------------------------------------------------------
// k6 options
// ---------------------------------------------------------------------------
export const options = {
  scenarios: {
    default: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERATIONS,
    },
  },
  thresholds: {
    'http_req_duration{name:GET /api/v0/applications}': ['p(95)<2000', 'p(99)<5000'],
    'http_req_duration{name:GET /api/v0/individuals}': ['p(95)<2000', 'p(99)<5000'],
  },
};

// ---------------------------------------------------------------------------
// Default request params
// ---------------------------------------------------------------------------
const DEFAULT_PARAMS = {
  headers: {
    Authorization: `Bearer ${__ENV.BEARER_TOKEN}`,
    'X-Service-Name': 'CIVIL_DECIDE',
    'Content-Type': 'application/json',
  },
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Returns a random integer between min (inclusive) and max (exclusive). */
function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min)) + min;
}

/** Sleep for a random duration between min and max ms (skipped when max is 0). */
function maybeThinkTime() {
  if (THINK_TIME_MAX_MS <= 0) return;
  const ms = THINK_TIME_MIN_MS === THINK_TIME_MAX_MS
    ? THINK_TIME_MIN_MS
    : THINK_TIME_MIN_MS + randomInt(0, THINK_TIME_MAX_MS - THINK_TIME_MIN_MS);
  if (ms > 0) sleep(ms / 1000);
}

/** Build a query string, skipping empty values. */
function queryString(params) {
  const parts = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && String(value).length > 0)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);

  return parts.length > 0 ? `?${parts.join('&')}` : '';
}

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

/**
 * GET /api/v0/applications
 * Returns an array of applicationId values (may be empty).
 */
function getApplicationIds() {
  const qs = queryString({
    page: 1,
    pageSize: APPLICATIONS_PAGE_SIZE,
    clientFirstName: CLIENT_FIRST_NAME,
    clientLastName: CLIENT_LAST_NAME,
    clientDateOfBirth: CLIENT_DATE_OF_BIRTH,
    status: STATUS,
  });

  const res = http.get(`${BASE_URL}/api/v0/applications${qs}`, {
    ...DEFAULT_PARAMS,
    tags: { name: 'GET /api/v0/applications' },
  });

  if (res.status !== 200) {
    console.log(`GET applications failed [${res.status}]: ${res.body}`);
    return [];
  }

  let body;
  try {
    body = JSON.parse(res.body);
  } catch (_) {
    return [];
  }

  if (!Array.isArray(body.applications)) return [];
  return body.applications
    .map((application) => application.applicationId)
    .filter(Boolean);
}

/**
 * GET /api/v0/individuals
 * Queries by applicationId and requests client details in the response.
 */
function getIndividuals(applicationId) {
  const qs = queryString({
    page: 1,
    pageSize: 20,
    applicationId,
    include: INCLUDE,
    individualType: INDIVIDUAL_TYPE,
  });

  const res = http.get(`${BASE_URL}/api/v0/individuals${qs}`, {
    ...DEFAULT_PARAMS,
    tags: { name: 'GET /api/v0/individuals' },
  });

  if (res.status < 200 || res.status >= 300) {
    console.log(`GET individuals failed [${res.status}] for application ${applicationId}: ${res.body}`);
  }
}

// ---------------------------------------------------------------------------
// Default function — one iteration
// ---------------------------------------------------------------------------
export default function () {
  // 1) Fetch a page of applications and collect IDs.
  const applicationIds = getApplicationIds();

  // 2) Pick one application ID and fetch linked individuals including client details.
  if (applicationIds.length > 0) {
    maybeThinkTime();
    const applicationId = applicationIds[randomInt(0, applicationIds.length)];
    getIndividuals(applicationId);
  }
}

