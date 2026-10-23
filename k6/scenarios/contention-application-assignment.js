import { Counter } from 'k6/metrics';

import { fetchTokens } from '../lib/auth.js';
import { loadConfig } from '../lib/config.js';
import { summary } from '../lib/reporting.js';
import {
  createScenarioContext,
  createScenarioRuntimes,
  runScenarioSteps,
} from '../lib/scenario-runtime.js';

const config = loadConfig(__ENV, ['provider', 'applicationCaseworker', 'contender']);
const scenario = 'contention-application-assignment';
const setupSteps = [
  'start-application-draft',
  'upload-application-document',
  'submit-application-draft',
  'record-manual-grant-eligibility',
  'find-application-in-open-applications',
];
const assignmentStep = ['assign-application-from-work-queue'];
const assignmentAttempts = new Counter('work_queue_assignment_attempts');

export const options = {
  scenarios: {
    contention: {
      executor: 'per-vu-iterations',
      vus: config.contentionVus,
      iterations: 1,
    },
  },
  thresholds: config.thresholds,
};

export function setup() {
  const tokens = fetchTokens(config, Object.keys(config.actors));
  const context = createScenarioContext();
  runScenarioSteps(setupSteps, config, tokens, context, scenario);
  return { tokens, context };
}

export default function ({ tokens, context: setupContext }) {
  const context = {
    ...setupContext,
    assignmentContention: true,
    metricTags: undefined,
  };
  const contenderRuntime = createScenarioRuntimes(config, tokens, context).Contender;
  runScenarioSteps(
    assignmentStep,
    config,
    tokens,
    context,
    scenario,
    { 'Application Caseworker': contenderRuntime },
  );
  assignmentAttempts.add(1, {
    scenario,
    result: context.applicationAssignment,
  });
  if (!['assigned', 'conflict'].includes(context.applicationAssignment)) {
    throw new Error('Contention assignment did not produce an assignment result');
  }
}

export const handleSummary = summary(config);
