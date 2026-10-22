import assert from 'node:assert/strict';
import test from 'node:test';

import { stepRegistry } from '../lib/step-registry.js';

const steps = Object.fromEntries(
  stepRegistry
    .filter((step) => step.actor === 'Application Caseworker' || step.actor === 'PA Caseworker')
    .map((step) => [step.canonicalName, step]),
);

const fixtures = {
  'fixtures/work-list/assign.json': { expectedAssignmentVersion: 0 },
  'fixtures/application/grant-decision.json': {
    overallDecision: 'GRANTED',
    applicationVersion: 0,
  },
  'fixtures/prior-authority/grant-decision.json': {
    decision: 'GRANTED',
    priorAuthorityVersion: 0,
  },
};

function response(status, body = {}) {
  return {
    status,
    json() {
      return body;
    },
  };
}

function runtime(responses, requests) {
  return {
    loadFixture(path) {
      return fixtures[path];
    },
    applyContext(value) {
      return structuredClone(value);
    },
    request(method, path, body, params) {
      requests.push({ method, path, body, params });
      return responses.shift();
    },
    expectStatus(result, statuses) {
      const expected = Array.isArray(statuses) ? statuses : [statuses];
      assert.ok(expected.includes(result.status), `expected ${expected}, received ${result.status}`);
      return result;
    },
    pollFor(description, predicate) {
      const result = predicate();
      assert.equal(result.matched, true, `expected ${description} to be observable`);
      return result.value;
    },
  };
}

test('application caseworker steps find, assign, verify, grant, and observe removal', () => {
  const requests = [];
  const context = {
    applicationId: 'application-123',
    runContext: {},
  };
  const dependencies = runtime([
    response(200, { items: [{ itemId: 'application-123', itemType: 'APPLICATION', assignmentVersion: 3 }] }),
    response(200),
    response(200, { items: [{ itemId: 'application-123', itemType: 'APPLICATION', assignmentVersion: 4 }] }),
    response(200),
    response(200, { items: [] }),
  ], requests);

  steps['Find application in Open Applications'].execute(context, dependencies);
  steps['Assign application from Work Queue'].execute(context, dependencies);
  steps['Find application in Personal Queue'].execute(context, dependencies);
  steps['Grant application'].execute(context, dependencies);

  assert.equal(context.applicationWorkListItem.assignmentVersion, 3);
  assert.equal(context.assignedApplicationWorkListItem.assignmentVersion, 4);
  assert.equal(context.grantedApplicationId, 'application-123');
  assert.deepEqual(requests.map(({ method, path, body }) => ({ method, path, body })), [
    { method: 'GET', path: '/api/v0/work-list?unassigned=true&itemType=APPLICATION', body: undefined },
    {
      method: 'POST',
      path: '/api/v0/work-list/application-123/assign',
      body: { expectedAssignmentVersion: 3 },
    },
    {
      method: 'GET',
      path: '/api/v0/work-list?assignedToMe=true&unassigned=false&itemType=APPLICATION',
      body: undefined,
    },
    {
      method: 'PATCH',
      path: '/api/v0/applications/application-123/decision',
      body: { overallDecision: 'GRANTED', applicationVersion: 0 },
    },
    { method: 'GET', path: '/api/v0/work-list?unassigned=true&itemType=APPLICATION', body: undefined },
  ]);
});

test('PA caseworker steps retain an independent Work Queue assignment', () => {
  const requests = [];
  const context = {
    submittedPriorAuthorityId: 'prior-authority-123',
    runContext: {},
  };
  const dependencies = runtime([
    response(200, { items: [{ itemId: 'prior-authority-123', itemType: 'PRIOR_AUTHORITY', assignmentVersion: 2 }] }),
    response(200),
    response(200, { items: [{ itemId: 'prior-authority-123', itemType: 'PRIOR_AUTHORITY', assignmentVersion: 3 }] }),
    response(204),
    response(200, { items: [] }),
  ], requests);

  steps['Find PA in Open Applications'].execute(context, dependencies);
  steps['Assign PA from Work Queue'].execute(context, dependencies);
  steps['Find PA in Personal Queue'].execute(context, dependencies);
  steps['Grant PA'].execute(context, dependencies);

  assert.equal(context.priorAuthorityWorkListItem.assignmentVersion, 2);
  assert.equal(context.assignedPriorAuthorityWorkListItem.assignmentVersion, 3);
  assert.equal(context.grantedPriorAuthorityId, 'prior-authority-123');
  assert.deepEqual(requests.map(({ method, path, body }) => ({ method, path, body })), [
    { method: 'GET', path: '/api/v0/work-list?unassigned=true&itemType=PRIOR_AUTHORITY', body: undefined },
    {
      method: 'POST',
      path: '/api/v0/work-list/prior-authority-123/assign',
      body: { expectedAssignmentVersion: 2 },
    },
    {
      method: 'GET',
      path: '/api/v0/work-list?assignedToMe=true&unassigned=false&itemType=PRIOR_AUTHORITY',
      body: undefined,
    },
    {
      method: 'PATCH',
      path: '/api/v0/prior-authorities/prior-authority-123/decision',
      body: { decision: 'GRANTED', priorAuthorityVersion: 0 },
    },
    { method: 'GET', path: '/api/v0/work-list?unassigned=true&itemType=PRIOR_AUTHORITY', body: undefined },
  ]);
});

test('assignment rejects conflicts unless the caller explicitly opts into contention classification', () => {
  const context = {
    applicationId: 'application-123',
    applicationWorkListItem: { itemId: 'application-123', assignmentVersion: 3 },
    runContext: {},
  };

  assert.throws(
    () =>
      steps['Assign application from Work Queue'].execute(
        context,
        runtime([response(409)], []),
      ),
    /expected 200, received 409/,
  );

  context.assignmentContention = true;
  steps['Assign application from Work Queue'].execute(context, runtime([response(409)], []));

  assert.equal(context.applicationAssignment, 'conflict');
});
