import assert from 'node:assert/strict';
import test from 'node:test';

import { executeSteps } from '../lib/journey-execution.js';

test('step execution uses the runtime associated with the documented actor and tags each step', () => {
  const calls = [];
  const steps = [
    {
      filename: 'start-application-draft',
      canonicalName: 'Start application draft',
      actor: 'Provider',
      execute(context, runtime) {
        calls.push({ name: this.canonicalName, runtime, tags: context.metricTags });
      },
    },
    {
      filename: 'grant-application',
      canonicalName: 'Grant application',
      actor: 'Application Caseworker',
      execute(context, runtime) {
        calls.push({ name: this.canonicalName, runtime, tags: context.metricTags });
      },
    },
  ];
  const providerRuntime = {};
  const caseworkerRuntime = {};

  executeSteps(
    ['start-application-draft', 'grant-application'],
    { runContext: {} },
    steps,
    {
      Provider: providerRuntime,
      'Application Caseworker': caseworkerRuntime,
    },
    'smoke-complete-journey',
  );

  assert.deepEqual(calls, [
    {
      name: 'Start application draft',
      runtime: providerRuntime,
      tags: { scenario: 'smoke-complete-journey', step: 'Start application draft' },
    },
    {
      name: 'Grant application',
      runtime: caseworkerRuntime,
      tags: { scenario: 'smoke-complete-journey', step: 'Grant application' },
    },
  ]);
});
