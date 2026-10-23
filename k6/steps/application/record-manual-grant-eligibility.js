import { defineStep } from '../define-step.js';
import { requireRuntime } from '../step-support.js';

export const recordManualGrantEligibility = defineStep({
  canonicalName: 'Record manual-grant eligibility',
  actor: 'Provider/system',
  inputs: ['applicationId', 'fixtures/application/manual-grant-outcome.json'],
  outputs: ['applicationVersion'],
  endpoint: 'PATCH /api/v0/applications/{id}/auto-grant-outcome',
  assertion: 'Returns HTTP 200 or 204 with a MANUAL outcome.',
  tags: ['smoke', 'performance', 'application', 'work-queue'],
}, (context, runtime) => {
  const dependencies = requireRuntime(runtime);
  const outcome = dependencies.applyContext(
    dependencies.loadFixture('fixtures/application/manual-grant-outcome.json'),
    context.runContext,
  );
  dependencies.expectStatus(
    dependencies.request(
      'PATCH',
      `/api/v0/applications/${context.applicationId}/auto-grant-outcome`,
      outcome,
      { headers: { 'Content-Type': 'application/json' } },
    ),
    [200, 204],
  );
  if (outcome.outcome !== 'MANUAL') {
    throw new Error('Manual-grant eligibility fixture must record the MANUAL outcome');
  }
  context.autoGrant = false;
});
