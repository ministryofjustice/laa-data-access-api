import { fetchTokens } from '../lib/auth.js';
import { loadConfig } from '../lib/config.js';
import { summary } from '../lib/reporting.js';
import { createScenarioContext, runScenarioSteps } from '../lib/scenario-runtime.js';

const config = loadConfig(__ENV, ['provider', 'applicationCaseworker']);
const scenario = 'performance-work-queue';
const steps = [
  'start-application-draft',
  'upload-application-document',
  'submit-application-draft',
  'record-manual-grant-eligibility',
  'find-application-in-open-applications',
  'assign-application-from-work-queue',
  'find-application-in-personal-queue',
  'grant-application',
  'verify-application-removed-from-open-applications',
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
