import { defineStep } from '../define-step.js';
import { requireIdentifier, requireRuntime, responseBody } from '../step-support.js';
import { composeDraft } from './create-application-draft.js';

export const startApplicationDraft = defineStep({
  canonicalName: 'Start application draft',
  actor: 'Provider',
  inputs: ['runContext', 'fixtures/application/create.json'],
  outputs: ['applicationDraftId'],
  endpoint: 'POST /api/v0/application-drafts',
  assertion: 'Returns HTTP 201 and an Application draft ID.',
  tags: ['smoke', 'performance', 'application', 'draft'],
}, (context, runtime) => {
  const dependencies = requireRuntime(runtime);
  const applicationDraft = composeDraft(dependencies, context.runContext);
  const response = dependencies.expectStatus(
    dependencies.request('POST', '/api/v0/application-drafts', applicationDraft, {
      headers: { 'Content-Type': 'application/json' },
    }),
    [201, 202],
  );
  context.applicationDraft = applicationDraft;
  context.applicationDraftId = requireIdentifier(
    responseBody(response),
    'applicationId',
    'Start application draft',
  );
});
