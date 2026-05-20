// Scenario picker. Tests call `selectScenario()` and get the correct executor
// config based on the K6_SCENARIO env var (defaulting to smoke).

import { env } from '../config/env.js';
import { smoke } from './smoke.js';
import { load } from './load.js';
import { stress } from './stress.js';
import { soak } from './soak.js';
import { spike } from './spike.js';
import { throughput } from './throughput.js';

const scenarios = { smoke, load, stress, soak, spike, throughput };

export const selectScenario = (name) => {
  const chosen = name ?? env.scenario;
  const config = scenarios[chosen];
  if (!config) {
    throw new Error(`Unknown scenario "${chosen}". Valid: ${Object.keys(scenarios).join(', ')}`);
  }
  return { [chosen]: config };
};
