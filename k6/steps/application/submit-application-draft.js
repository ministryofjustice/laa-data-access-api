import { defineStep } from '../define-step.js';
import { requireIdentifier, requireRuntime, responseBody } from '../step-support.js';

export const submitApplicationDraft = defineStep({
  canonicalName: 'Submit application draft',
  actor: 'Provider',
  inputs: ['applicationDraftId'],
  outputs: ['applicationId'],
  endpoint: 'POST /api/v0/application-drafts/{applicationId}/submit',
  assertion: 'Returns HTTP 200 or 202 and an Application ID.',
  tags: ['smoke', 'performance', 'application', 'submission'],
}, (context, runtime) => {
  const dependencies = requireRuntime(runtime);
  const response = dependencies.expectStatus(
    dependencies.request(
      'POST',
      `/api/v0/application-drafts/${context.applicationDraftId}/submit`,
      null,
    ),
    [200, 202],
  );
  context.applicationId = requireIdentifier(
    responseBody(response),
    'applicationId',
    'Submit application draft',
  );
});
