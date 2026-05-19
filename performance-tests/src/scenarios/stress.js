// Stress scenario — push beyond expected peak to find the breaking point.
// Use for: capacity planning, identifying first bottleneck.

import { env } from '../config/env.js';

export const stress = {
  executor: 'ramping-vus',
  startVUs: 0,
  stages: env.durationOverride
    ? [{ target: env.vusOverride ?? 100, duration: env.durationOverride }]
    : [
        { target: 50, duration: '2m' },
        { target: 100, duration: '5m' },
        { target: env.vusOverride ?? 150, duration: '5m' },
        { target: 0, duration: '1m' },
      ],
  gracefulRampDown: '30s',
  tags: { scenario: 'stress' },
};
