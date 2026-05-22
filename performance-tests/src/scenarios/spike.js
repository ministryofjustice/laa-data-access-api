// Spike scenario — sudden burst of traffic, then drop.
// Use for: validating autoscaling, graceful recovery from traffic surge.

import { env } from '../config/env.js';

export const spike = {
  executor: 'ramping-vus',
  startVUs: 0,
  stages: env.durationOverride
    ? [
        { target: env.vusOverride || 200, duration: '30s' },
        { target: env.vusOverride || 200, duration: env.durationOverride },
        { target: 0, duration: '30s' },
      ]
    : [
        { target: env.vusOverride || 200, duration: '30s' },
        { target: env.vusOverride || 200, duration: '1m' },
        { target: 0, duration: '30s' },
      ],
  gracefulRampDown: '15s',
  tags: { scenario: 'spike' },
};
