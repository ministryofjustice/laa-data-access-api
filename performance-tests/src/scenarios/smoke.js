// Smoke scenario — minimum load to prove the test wiring works.
// Use for: PR validation, post-deploy sanity, "does this script still run".

import { env } from '../config/env.js';

export const smoke = {
  executor: 'constant-vus',
  vus: env.vusOverride ?? 1,
  duration: env.durationOverride ?? '30s',
  tags: { scenario: 'smoke' },
};
