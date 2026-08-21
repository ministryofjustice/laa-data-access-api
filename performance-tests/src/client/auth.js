// Auth header construction.
//
// Two modes:
//   - Static (default): K6_TOKEN is used for every request. Fine for runs
//     shorter than the token's lifetime.
//   - Refresh: if K6_TOKEN_REFRESH_URL is set, the token is treated as
//     refreshable — re-fetched after K6_TOKEN_TTL_SECONDS expires. Necessary
//     for soak scenarios where a single token would expire mid-run.
//
// The refresh endpoint is expected to accept the current bearer token and
// return JSON `{ "access_token": "..." }`. Adapt parseRefreshResponse if your
// provider uses a different shape.

import http from 'k6/http';
import { env } from '../config/env.js';

let cachedToken = env.token;
let expiresAtMs = env.tokenRefreshUrl ? 0 : Number.POSITIVE_INFINITY;

const parseRefreshResponse = (res) => {
  try {
    const body = JSON.parse(res.body);
    return body.access_token || body.token || null;
  } catch (_) {
    return null;
  }
};

const refreshToken = () => {
  if (!env.tokenRefreshUrl) return cachedToken;
  const res = http.post(
    env.tokenRefreshUrl,
    JSON.stringify({}),
    {
      headers: {
        Authorization: `Bearer ${cachedToken}`,
        'Content-Type': 'application/json',
      },
      timeout: '5s',
      tags: { name: 'auth refresh' },
    },
  );
  const fresh = parseRefreshResponse(res);
  if (!fresh) {
    console.warn(`Token refresh failed (${res.status}); reusing existing token`);
    return cachedToken;
  }
  cachedToken = fresh;
  expiresAtMs = Date.now() + env.tokenTtlSeconds * 1000;
  return cachedToken;
};

const currentToken = () => {
  if (Date.now() >= expiresAtMs) refreshToken();
  return cachedToken;
};

export const authHeaders = () => ({
  Authorization: `Bearer ${currentToken()}`,
  'X-Service-Name': env.serviceName,
  Accept: 'application/json',
});
