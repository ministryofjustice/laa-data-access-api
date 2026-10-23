import { fetchTokens } from '../lib/auth.js';
import { loadConfig } from '../lib/config.js';
import { summary } from '../lib/reporting.js';
import { createScenarioContext, runScenarioSteps } from '../lib/scenario-runtime.js';

const config = loadConfig(__ENV, ['provider']);
const scenario = 'performance-provider-drafting';
const steps = [
  'Start application draft',
  ...Array.from(
    { length: 15 },
    (_, index) => `Update application draft: ${String(index + 1).padStart(2, '0')}`,
  ),
  'Upload application document',
  'Submit application draft',
];

export const options = {
  vus: config.performanceVus,
  duration: config.performanceDuration,
  thresholds: config.thresholds,
};

export function setup() {
  return fetchTokens(config, Object.keys(config.actors));
}

export default function (tokens) {
  runScenarioSteps(steps, config, tokens, createScenarioContext(), scenario);
}

export const handleSummary = summary(config);
