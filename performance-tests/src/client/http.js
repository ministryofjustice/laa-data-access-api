// Thin wrapper around k6/http that:
//   - merges auth headers
//   - tags every request with `name` (so per-endpoint thresholds work)
//   - applies a default timeout from env (override per-call via params.timeout)
//   - logs the first N failures per request name so debugging non-2xxs is easy
//     without spamming when a whole run is failing.

import http from 'k6/http';
import { authHeaders } from './auth.js';
import { env } from '../config/env.js';

// Per-name failure budget — we log the first 5 failures per request name and
// drop subsequent ones to keep logs readable in a failing run.
const MAX_LOGGED_FAILURES_PER_NAME = 5;
const failureCounts = new Map();

const logIfFailed = (name, res) => {
  if (res.status >= 200 && res.status < 400) return;
  const seen = failureCounts.get(name) || 0;
  if (seen >= MAX_LOGGED_FAILURES_PER_NAME) return;
  failureCounts.set(name, seen + 1);
  const bodyExcerpt = typeof res.body === 'string' ? res.body.slice(0, 200) : '(non-text body)';
  console.warn(`[${name}] ${res.status} ${res.error || ''} — ${bodyExcerpt}`);
};

const mergeParams = (name, params) => {
  const p = params || {};
  return Object.assign({ timeout: env.requestTimeout }, p, {
    headers: Object.assign({}, authHeaders(), p.headers || {}),
    tags: Object.assign({}, p.tags || {}, { name: name }),
  });
};

const withJsonBody = (params) => {
  const p = params || {};
  return Object.assign({}, p, {
    headers: Object.assign({ 'Content-Type': 'application/json' }, p.headers || {}),
  });
};

export const get = (name, url, params) => {
  const res = http.get(url, mergeParams(name, params));
  logIfFailed(name, res);
  return res;
};

export const post = (name, url, body, params) => {
  const res = http.post(url, body, mergeParams(name, withJsonBody(params)));
  logIfFailed(name, res);
  return res;
};

export const patch = (name, url, body, params) => {
  const res = http.patch(url, body, mergeParams(name, withJsonBody(params)));
  logIfFailed(name, res);
  return res;
};

export const del = (name, url, params) => {
  const res = http.del(url, null, mergeParams(name, params));
  logIfFailed(name, res);
  return res;
};
