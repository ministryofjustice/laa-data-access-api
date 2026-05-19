// Thin wrapper around k6/http that:
//   - merges auth headers
//   - tags every request with `name` (so per-endpoint thresholds work)
//   - returns the raw k6 Response so tests can run their own checks

import http from 'k6/http';
import { authHeaders } from './auth.js';

const mergeParams = (name, params = {}) => ({
  ...params,
  headers: { ...authHeaders(), ...(params.headers || {}) },
  tags: { ...(params.tags || {}), name },
});

export const get = (name, url, params) => http.get(url, mergeParams(name, params));

export const post = (name, url, body, params) =>
  http.post(url, body, mergeParams(name, { ...params, headers: { 'Content-Type': 'application/json', ...(params?.headers || {}) } }));

export const patch = (name, url, body, params) =>
  http.patch(url, body, mergeParams(name, { ...params, headers: { 'Content-Type': 'application/json', ...(params?.headers || {}) } }));

export const del = (name, url, params) => http.del(url, null, mergeParams(name, params));
