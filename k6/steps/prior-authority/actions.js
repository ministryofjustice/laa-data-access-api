import { requireIdentifier, requireRuntime, responseBody } from '../step-support.js';

export function start(context, runtime) {
  const dependencies = requireRuntime(runtime);
  if (!context.grantedApplicationId) throw new Error('Start Prior Authority draft requires grantedApplicationId');
  const draft = dependencies.applyContext(dependencies.loadFixture('fixtures/prior-authority/create.json'), context.runContext);
  draft.applicationId = context.grantedApplicationId;
  const response = dependencies.expectStatus(dependencies.request('POST', '/api/v0/prior-authorities', draft, {
    headers: { 'Content-Type': 'application/json' },
  }), [201, 202]);
  context.priorAuthorityId = requireIdentifier(responseBody(response), 'priorAuthorityId', 'Start Prior Authority draft');
}

export function update(context, runtime) {
  const dependencies = requireRuntime(runtime);
  if (!context.priorAuthorityId) throw new Error('Update Prior Authority draft requires priorAuthorityId');
  const body = dependencies.applyContext(dependencies.loadFixture('fixtures/prior-authority/update.json'), context.runContext);
  dependencies.expectStatus(dependencies.request('PUT', `/api/v0/prior-authorities/${context.priorAuthorityId}`, body, {
    headers: { 'Content-Type': 'application/json' },
  }), 204);
}

export function uploadDocument(context, runtime) {
  const dependencies = requireRuntime(runtime);
  if (!context.priorAuthorityId) throw new Error('Upload Prior Authority document requires priorAuthorityId');
  const response = dependencies.expectStatus(dependencies.request('POST', `/api/v0/prior-authorities/${context.priorAuthorityId}/documents`, {
    file: dependencies.documentFixture('fixtures/documents/k6-evidence.pdf'),
  }), 201);
  context.priorAuthorityDocumentId = requireIdentifier(responseBody(response), 'documentId', 'Upload Prior Authority document');
}

export function updateDocumentType(context, runtime) {
  const dependencies = requireRuntime(runtime);
  if (!context.priorAuthorityId || !context.priorAuthorityDocumentId) {
    throw new Error('Update Prior Authority document type requires priorAuthorityId and priorAuthorityDocumentId');
  }
  const body = dependencies.applyContext(
    dependencies.loadFixture('fixtures/prior-authority/document-type.json'),
    context.runContext,
  );
  dependencies.expectStatus(
    dependencies.request(
      'PATCH',
      `/api/v0/prior-authorities/${context.priorAuthorityId}/documents/${context.priorAuthorityDocumentId}`,
      body,
      { headers: { 'Content-Type': 'application/json' } },
    ),
    200,
  );
}

export function submit(context, runtime) {
  const dependencies = requireRuntime(runtime);
  if (!context.priorAuthorityId) throw new Error('Submit Prior Authority requires priorAuthorityId');
  const response = dependencies.expectStatus(dependencies.request('POST', `/api/v0/prior-authorities/${context.priorAuthorityId}/submit`, null), [200, 202]);
  context.submittedPriorAuthorityId = requireIdentifier(responseBody(response), 'priorAuthorityId', 'Submit Prior Authority');
}
