// Soak scenario — extended runtime at moderate load.
// Use for: detecting memory leaks, connection pool exhaustion, slow degradation.

import { env } from '../config/env.js';

export const soak = {
  executor: 'constant-vus',
  vus: env.vusOverride ?? 20,
  duration: env.durationOverride ?? '2h',
  tags: { scenario: 'soak' },
};
