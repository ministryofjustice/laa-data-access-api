import { defineStep } from '../define-step.js';
import { requireIdentifier, requireRuntime, responseBody } from '../step-support.js';

export const uploadApplicationDocument = defineStep({
  canonicalName: 'Upload application document',
  actor: 'Provider',
  inputs: ['applicationId', 'fixtures/documents/k6-evidence.pdf'],
  outputs: ['applicationDocumentId'],
  endpoint: 'POST /api/v0/applications/{id}/documents',
  assertion: 'Returns HTTP 201 and a document ID.',
  tags: ['smoke', 'performance', 'application', 'document'],
}, (context, runtime) => {
  const dependencies = requireRuntime(runtime);
  const response = dependencies.expectStatus(
    dependencies.request(
      'POST',
      `/api/v0/applications/${context.applicationId || context.applicationDraftId}/documents`,
      {
        file: dependencies.documentFixture('fixtures/documents/k6-evidence.pdf'),
        documentType: 'GATEWAY_EVIDENCE',
      },
    ),
    201,
  );
  context.applicationDocumentId = requireIdentifier(
    responseBody(response),
    'documentId',
    'Upload application document',
  );
});
