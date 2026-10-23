import { fetchTokens } from '../lib/auth.js';
import { loadConfig } from '../lib/config.js';
import { summary } from '../lib/reporting.js';
import { createScenarioContext, runScenarioSteps } from '../lib/scenario-runtime.js';

const config = loadConfig(__ENV, ['provider', 'applicationCaseworker']);
const scenario = 'performance-complete-journey';
const steps = [
  'Start application draft',
  ...Array.from(
    { length: 15 },
    (_, index) => `Update application draft: ${String(index + 1).padStart(2, '0')}`,
  ),
  'Upload application document',
  'Submit application draft',
  'Record manual-grant eligibility',
  'Find application in Open Applications',
  'Assign application from Work Queue',
  'Find application in Personal Queue',
  'Grant application',
  'List applications',
  'Start Prior Authority draft',
  'Update Prior Authority draft',
  'Upload Prior Authority document',
  'Submit Prior Authority',
  'Find PA in Open Applications',
  'Assign PA from Work Queue',
  'Find PA in Personal Queue',
  'Grant PA',
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
