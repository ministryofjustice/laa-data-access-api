// Throughput scenario — open-model (constant-arrival-rate).
//
// Closed-model scenarios (smoke/load/stress) fix the number of VUs; if the API
// slows down, those VUs spend more time waiting and the effective throughput
// drops. That's fine for "can we handle 20 concurrent users" but useless for
// answering "can we hold 50 req/s?". Open-model fires requests at a fixed rate
// and pre-allocates the VUs needed to sustain it.
//
// Configure with K6_VUS_OVERRIDE = req/s, K6_DURATION_OVERRIDE = how long.

import { env } from '../config/env.js';

const targetRps = env.vusOverride || 50;

export const throughput = {
  executor: 'constant-arrival-rate',
  rate: targetRps,
  timeUnit: '1s',
  duration: env.durationOverride || '2m',
  // Allocate enough VUs to cover a tail-latency spike. Rule of thumb: 2x rate
  // for sub-second endpoints; bump higher if your p99 is >1s.
  preAllocatedVUs: targetRps * 2,
  maxVUs: targetRps * 5,
  tags: { scenario: 'throughput' },
};
