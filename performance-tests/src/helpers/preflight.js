// Preflight health check.
//
// k6 runs setup() once before any VU starts. We use it to fail fast if the
// target URL is unreachable — otherwise a misconfigured K6_TARGET_URL produces
// a full run of 404s and someone wastes 30 minutes wondering what went wrong.

import http from 'k6/http';
import { env } from '../config/env.js';
import { authHeaders } from '../client/auth.js';

const resolveHealthUrl = () => {
  if (env.healthUrl) return env.healthUrl;
  const base = env.targetUrl.replace(/\/$/, '');
  return `${base}/actuator/health`;
};

// Default setup() to re-export from your test:
//   import { preflight as setup } from '../src/helpers/preflight.js';
//   export { setup };
//
// Returns the resolved health URL so tests can chain extra setup if needed.
export const preflight = () => {
  const url = resolveHealthUrl();
  const res = http.get(url, { headers: authHeaders(), timeout: '5s', tags: { name: 'preflight' } });

  if (res.status < 200 || res.status >= 300) {
    throw new Error(
      `Preflight failed: ${url} → ${res.status}. ` +
        `Check K6_TARGET_URL and K6_TOKEN, or set K6_HEALTH_URL to override the health endpoint.`,
    );
  }

  console.log(`Preflight OK: ${url} (${res.status})`);
  return { healthUrl: url };
};
