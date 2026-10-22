import assert from 'node:assert/strict';
import test from 'node:test';

import { stepRegistry } from '../lib/step-registry.js';

const applicationSteps = Object.fromEntries(
  stepRegistry
    .filter((step) => step.actor === 'Provider' || step.actor === 'Provider/system')
    .map((step) => [step.canonicalName, step]),
);

const fixtures = {
  'fixtures/application/create.json': {
    id: '{{applicationId}}',
    status: 'APPLICATION_SUBMITTED',
    laaReference: '{{laaReference}}',
    applicationContent: { createdAt: '2026-01-01T00:00:00Z' },
  },
  'fixtures/application/update-01.json': { provider: { officeCode: '0Z123A' } },
  'fixtures/application/update-02.json': { client: { lastName: '{{clientId}}' } },
  'fixtures/application/update-03.json': { proceedings: [{ meaning: 'Final hearing' }] },
  'fixtures/application/manual-grant-outcome.json': { outcome: 'MANUAL' },
  'fixtures/prior-authority/create.json': {
    applicationId: '{{applicationId}}',
    priorAuthorityType: 'EXPERT',
  },
  'fixtures/prior-authority/update.json': {
    justification: 'A synthetic independent expert assessment is required for this test.',
  },
  'fixtures/prior-authority/submit.json': {},
};

function runtime(requests) {
  return {
    loadFixture(path) {
      return fixtures[path] || {};
    },
    applyContext(value, context) {
      return JSON.parse(
        JSON.stringify(value)
          .replaceAll('{{applicationId}}', context.applicationId)
          .replaceAll('{{laaReference}}', context.laaReference)
          .replaceAll('{{clientId}}', context.clientId),
      );
    },
    request(method, path, body, params) {
      requests.push({ method, path, body, params });
      return {
        status: path.endsWith('/submit') ? 200 : path.endsWith('/auto-grant-outcome') ? 204 : 201,
        json() {
          return path === '/api/v0/application-drafts' || path.endsWith('/submit')
            ? { applicationId: 'draft-123' }
            : { documentId: 'document-123' };
        },
      };
    },
    expectStatus(response, statuses) {
      assert.ok((Array.isArray(statuses) ? statuses : [statuses]).includes(response.status));
      return response;
    },
    documentFixture() {
      return 'pdf-bytes';
    },
  };
}

test('provider application steps compose versioned updates before saving a draft', () => {
  const requests = [];
  const context = {
    runContext: {
      applicationId: 'draft-123',
      laaReference: 'LAA-K6-123',
      clientId: 'k6-client-123',
    },
  };
  const dependencies = runtime(requests);

  applicationSteps['Start application draft'].execute(context, dependencies);
  for (let sequence = 1; sequence <= 15; sequence += 1) {
    applicationSteps[`Update application draft: ${String(sequence).padStart(2, '0')}`].execute(
      context,
      dependencies,
    );
  }

  assert.equal(requests.length, 1);
  assert.deepEqual(requests[0], {
    method: 'POST',
    path: '/api/v0/application-drafts',
    body: {
      id: 'draft-123',
      status: 'APPLICATION_SUBMITTED',
      laaReference: 'LAA-K6-123',
      applicationContent: {
        createdAt: '2026-01-01T00:00:00Z',
        provider: { officeCode: '0Z123A' },
        client: { lastName: 'k6-client-123' },
        proceedings: [{ meaning: 'Final hearing' }],
      },
    },
    params: { headers: { 'Content-Type': 'application/json' } },
  });
  assert.equal(context.applicationDraftId, 'draft-123');
  assert.equal(context.applicationDraft.applicationContent.client.lastName, 'k6-client-123');
});

test('provider application mutations retain the identifiers required by later steps', () => {
  const requests = [];
  const context = {
    runContext: { applicationId: 'draft-123', laaReference: 'LAA-K6-123', clientId: 'k6-client-123' },
    applicationDraftId: 'draft-123',
  };
  const dependencies = runtime(requests);

  applicationSteps['Upload application document'].execute(context, dependencies);
  applicationSteps['Submit application draft'].execute(context, dependencies);
  applicationSteps['Record manual-grant eligibility'].execute(context, dependencies);

  assert.equal(context.applicationDocumentId, 'document-123');
  assert.equal(context.applicationId, 'draft-123');
  assert.equal(context.autoGrant, false);
  assert.deepEqual(
    requests.map(({ method, path }) => ({ method, path })),
    [
      { method: 'POST', path: '/api/v0/applications/draft-123/documents' },
      { method: 'POST', path: '/api/v0/application-drafts/draft-123/submit' },
      { method: 'PATCH', path: '/api/v0/applications/draft-123/auto-grant-outcome' },
    ],
  );
});

test('provider application listing records the granted parent found through the API', () => {
  const requests = [];
  const context = {
    runContext: { laaReference: 'LAA-K6-123' },
    applicationId: 'application-123',
  };
  const dependencies = {
    ...runtime(requests),
    request(method, path) {
      requests.push({ method, path });
      return {
        status: 200,
        json() {
          return { applications: [{ applicationId: 'application-123', status: 'GRANTED' }] };
        },
      };
    },
  };

  applicationSteps['List applications'].execute(context, dependencies);

  assert.equal(context.grantedApplicationId, 'application-123');
  assert.deepEqual(requests, [
    { method: 'GET', path: '/api/v0/applications?laaReference=LAA-K6-123' },
  ]);
});

test('provider can create, update, upload, and submit a Prior Authority against the granted Application', () => {
  const requests = [];
  const context = {
    grantedApplicationId: 'application-123',
    runContext: { applicationId: 'draft-123', laaReference: 'LAA-K6-123', clientId: 'k6-client-123' },
  };
  const dependencies = {
    ...runtime(requests),
    request(method, path, body, params) {
      requests.push({ method, path, body, params });
      return {
        status: method === 'POST' && path === '/api/v0/prior-authorities' ? 201
          : method === 'PUT' ? 204
            : method === 'POST' && path.endsWith('/documents') ? 201
              : 202,
        json() {
          return path === '/api/v0/prior-authorities'
            ? { priorAuthorityId: 'prior-authority-123' }
            : path.endsWith('/documents')
              ? { documentId: 'document-123' }
              : { priorAuthorityId: 'prior-authority-123' };
        },
      };
    },
  };

  applicationSteps['Start Prior Authority draft'].execute(context, dependencies);
  applicationSteps['Update Prior Authority draft'].execute(context, dependencies);
  applicationSteps['Upload Prior Authority document'].execute(context, dependencies);
  applicationSteps['Submit Prior Authority'].execute(context, dependencies);

  assert.equal(context.priorAuthorityId, 'prior-authority-123');
  assert.equal(context.priorAuthorityDocumentId, 'document-123');
  assert.equal(context.submittedPriorAuthorityId, 'prior-authority-123');
  assert.deepEqual(requests, [
    {
      method: 'POST',
      path: '/api/v0/prior-authorities',
      body: { applicationId: 'application-123', priorAuthorityType: 'EXPERT' },
      params: { headers: { 'Content-Type': 'application/json' } },
    },
    {
      method: 'PUT',
      path: '/api/v0/prior-authorities/prior-authority-123',
      body: {
        justification: 'A synthetic independent expert assessment is required for this test.',
      },
      params: { headers: { 'Content-Type': 'application/json' } },
    },
    {
      method: 'POST',
      path: '/api/v0/prior-authorities/prior-authority-123/documents',
      body: { file: 'pdf-bytes' },
      params: undefined,
    },
    {
      method: 'POST',
      path: '/api/v0/prior-authorities/prior-authority-123/submit',
      body: null,
      params: undefined,
    },
  ]);
});
