import http from 'k6/http';
import { sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ---------------------------------------------------------------------------
// Fixture: load once at init time, mutate per iteration
// ---------------------------------------------------------------------------
const APPLICATION_CONTENT_TEMPLATE = JSON.parse(open('./fixtures/applicationContent.json'));

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = parseInt(__ENV.VUS || '5', 10);
const ITERATIONS = parseInt(__ENV.ITERATIONS || '100', 10);
const THINK_TIME_MIN_MS = parseInt(__ENV.THINK_TIME_MIN_MS || '0', 10);
const THINK_TIME_MAX_MS = parseInt(__ENV.THINK_TIME_MAX_MS || '0', 10);

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
    'http_req_duration{name:POST /api/v0/applications}': ['p(95)<2000', 'p(99)<5000'],
    'http_req_duration{name:GET /api/v0/applications/{id}}': ['p(95)<2000', 'p(99)<5000'],
    'http_req_duration{name:PATCH /api/v0/applications/{id}/decision}': ['p(95)<2000', 'p(99)<5000'],
  },
};

// ---------------------------------------------------------------------------
// Default request params (auth headers applied to every request)
// ---------------------------------------------------------------------------
const DEFAULT_PARAMS = {
  headers: {
    'Authorization': `Bearer ${__ENV.BEARER_TOKEN}`,
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

/** Returns a random date string for a person aged between 18 and 80. */
function randomDateOfBirth() {
  const yearsAgo = randomInt(18, 80);
  const d = new Date();
  d.setFullYear(d.getFullYear() - yearsAgo);
  return d.toISOString().slice(0, 10);
}

/**
 * Builds a random sentence from a small word bank — avoids external dependencies.
 * Used for meritsDecision justification and reason fields.
 */
function randomSentence() {
  const words = [
    'The', 'application', 'has', 'been', 'reviewed', 'and', 'considered',
    'in', 'light', 'of', 'the', 'available', 'evidence', 'does', 'not',
    'meet', 'the', 'merits', 'criteria', 'at', 'this', 'stage',
  ];
  const len = randomInt(6, 12);
  const picked = [];
  for (let i = 0; i < len; i++) {
    picked.push(words[randomInt(0, words.length)]);
  }
  return picked.join(' ') + '.';
}

/**
 * Shallow-mutates a clone of the fixture template with fresh per-iteration values:
 *   - new laaReference
 *   - new submittedAt / createdAt / updatedAt / applicationRef timestamps
 *   - new UUIDs for id, applicantId, providerId, officeId, copyCaseId
 *   - fresh proceeding UUIDs and timestamps
 */
function buildApplicationContent() {
  // Deep-clone by round-tripping through JSON so mutations don't bleed across iterations.
  const content = JSON.parse(JSON.stringify(APPLICATION_CONTENT_TEMPLATE));

  const now = new Date().toISOString();
  const laaRef = `L-${randomChars(3)}-${randomChar()}${randomInt(10, 99)}-${randomInt(1, 9)}`;

  content.id = uuidv4();
  content.submittedAt = now;
  content.laaReference = laaRef;
  content.applicationRef = laaRef;
  content.createdAt = now;
  content.updatedAt = now;
  content.clientDeclarationConfirmedAt = now;
  content.applicantId = uuidv4();
  content.providerId = uuidv4();
  content.officeId = uuidv4();
  content.copyCaseId = uuidv4();

  if (Array.isArray(content.proceedings)) {
    content.proceedings = content.proceedings.map((p) => ({
      ...p,
      id: uuidv4(),
      legalAidApplicationId: uuidv4(),
      createdAt: now,
      updatedAt: now,
      scopeLimitations: Array.isArray(p.scopeLimitations)
        ? p.scopeLimitations.map((sl) => ({ ...sl, id: uuidv4() }))
        : [],
    }));
  }

  if (Array.isArray(content.proceedingMerits)) {
    content.proceedingMerits = content.proceedingMerits.map((pm, i) => ({
      ...pm,
      id: uuidv4(),
      proceedingId: content.proceedings[i] ? content.proceedings[i].id : uuidv4(),
    }));
  }

  return content;
}

/** 3 random uppercase letters. */
function randomChars(n) {
  let s = '';
  for (let i = 0; i < n; i++) {
    s += String.fromCharCode(65 + randomInt(0, 26));
  }
  return s;
}

/** 1 random uppercase letter. */
function randomChar() {
  return randomChars(1);
}

/** Sleep for a random duration between min and max ms (skipped when max is 0). */
function maybeThinkTime() {
  if (THINK_TIME_MAX_MS <= 0) return;
  const ms = THINK_TIME_MIN_MS === THINK_TIME_MAX_MS
    ? THINK_TIME_MIN_MS
    : THINK_TIME_MIN_MS + randomInt(0, THINK_TIME_MAX_MS - THINK_TIME_MIN_MS);
  if (ms > 0) sleep(ms / 1000);
}

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

/**
 * POST /api/v0/applications
 * Returns the applicationId UUID string parsed from the Location header, or null on failure.
 */
function postCreateApplication() {
  const content = buildApplicationContent();

  const body = JSON.stringify({
    status: 'APPLICATION_IN_PROGRESS',
    laaReference: content.laaReference,
    individuals: [
      {
        firstName: randomChars(randomInt(4, 8)),
        lastName: randomChars(randomInt(4, 10)),
        dateOfBirth: randomDateOfBirth(),
        type: 'CLIENT',
        details: { source: 'perf-test' },
      },
    ],
    applicationContent: content,
  });

  const res = http.post(`${BASE_URL}/api/v0/applications`, body, {
    ...DEFAULT_PARAMS,
    tags: { name: 'POST /api/v0/applications' },
  });

  if (res.status !== 201) {
    return null;
  }

  // Location header: e.g. /api/v0/applications/550e8400-e29b-41d4-a716-446655440000
  const location = res.headers['Location'] || res.headers['location'] || '';
  const parts = location.split('/');
  return parts[parts.length - 1] || null;
}

/**
 * GET /api/v0/applications/{id}
 * Returns an array of proceedingId strings (may be empty).
 */
function getApplicationProceedings(applicationId) {
  const res = http.get(`${BASE_URL}/api/v0/applications/${applicationId}`, {
    ...DEFAULT_PARAMS,
    tags: { name: 'GET /api/v0/applications/{id}' },
  });

  if (res.status !== 200) {
    return [];
  }

  let body;
  try {
    body = JSON.parse(res.body);
  } catch (_) {
    return [];
  }

  if (!Array.isArray(body.proceedings)) return [];
  return body.proceedings
    .map((p) => p.proceedingId)
    .filter(Boolean);
}

/**
 * PATCH /api/v0/applications/{id}/decision
 * Sends a REFUSED decision for every supplied proceeding ID.
 */
function patchDecision(applicationId, proceedingIds) {
  const proceedings = proceedingIds.map((proceedingId) => ({
    proceedingId,
    meritsDecision: {
      decision: 'REFUSED',
      justification: randomSentence(),
      reason: randomSentence(),
    },
  }));

  const body = JSON.stringify({
    overallDecision: 'REFUSED',
    autoGranted: false,
    applicationVersion: 0,
    eventHistory: { eventDescription: 'Performance test decision' },
    proceedings,
  });

  const res = http.patch(`${BASE_URL}/api/v0/applications/${applicationId}/decision`, body, {
    ...DEFAULT_PARAMS,
    tags: { name: 'PATCH /api/v0/applications/{id}/decision' },
  });

  if (res.status < 200 || res.status >= 300) {
    console.log(`PATCH decision failed [${res.status}] for application ${applicationId}: ${res.body}`);
  }
}

// ---------------------------------------------------------------------------
// Default function — one iteration
// ---------------------------------------------------------------------------
export default function () {
  // 1. Create application
  const applicationId = postCreateApplication();

  // 2. Optionally make a decision
  if (applicationId !== null) {
    maybeThinkTime();

    const proceedingIds = getApplicationProceedings(applicationId);

    if (proceedingIds.length > 0) {
      maybeThinkTime();
      patchDecision(applicationId, proceedingIds);
    }
  }
}

