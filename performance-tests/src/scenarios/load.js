// Load scenario — sustained expected production traffic.
// Use for: validating SLOs hold under typical demand.

import { env } from '../config/env.js';

export const load = {
  executor: 'ramping-vus',
  startVUs: 0,
  stages: env.durationOverride
    ? [{ target: env.vusOverride || 20, duration: env.durationOverride }]
    : [
        { target: env.vusOverride || 20, duration: '1m' },
        { target: env.vusOverride || 20, duration: '5m' },
        { target: 0, duration: '30s' },
      ],
  gracefulRampDown: '30s',
  tags: { scenario: 'load' },
};
