import { fetchTokens } from '../lib/auth.js';
import { loadConfig } from '../lib/config.js';
import { summary } from '../lib/reporting.js';
import { createScenarioContext, runScenarioSteps } from '../lib/scenario-runtime.js';

const config = loadConfig(__ENV, ['provider', 'applicationCaseworker']);
const scenario = 'smoke-complete-journey';
const steps = [
  'start-application-draft',
  //'upload-application-document',
  'submit-application-draft',
  'record-manual-grant-eligibility',
  'find-application-in-open-applications',
  'assign-application-from-work-queue',
  'find-application-in-personal-queue',
  'grant-application',
  'verify-application-removed-from-open-applications',
  'list-applications',
  'start-prior-authority-draft',
  'update-prior-authority-draft',
  //'upload-prior-authority-document',
  //'update-prior-authority-document-type',
  'submit-prior-authority',
  'find-pa-in-open-applications',
  'assign-pa-from-work-queue',
  'find-pa-in-personal-queue',
  'grant-pa',
  'verify-pa-removed-from-open-applications',
];

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: config.thresholds,
};

export function setup() {
  return fetchTokens(config, Object.keys(config.actors));
}

export default function (tokens) {
  runScenarioSteps(steps, config, tokens, createScenarioContext(), scenario);
}

export const handleSummary = summary(config);
