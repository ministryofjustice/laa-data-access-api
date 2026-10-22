const REQUIRED_METADATA = [
  'canonicalName',
  'actor',
  'inputs',
  'outputs',
  'endpoint',
  'assertion',
  'tags',
];

function requireRuntime(runtime) {
  if (!runtime || typeof runtime.loadFixture !== 'function' || typeof runtime.request !== 'function') {
    throw new Error('Provider Application steps require fixture loading and HTTP request functions');
  }
  return runtime;
}

function responseBody(response) {
  if (!response || typeof response.json !== 'function') {
    throw new Error('Expected a JSON HTTP response');
  }
  return response.json();
}

function requireIdentifier(body, key, step) {
  if (!body || !body[key]) {
    throw new Error(`${step} response did not contain ${key}`);
  }
  return body[key];
}

function composeDraft(runtime, runContext) {
  const create = runtime.applyContext(
    runtime.loadFixture('fixtures/application/create.json'),
    runContext,
  );
  const updates = Array.from({ length: 15 }, (_, index) => {
    const sequence = String(index + 1).padStart(2, '0');
    return runtime.loadFixture(`fixtures/application/update-${sequence}.json`);
  });
  const applicationContent = runtime.composeApplicationContent
    ? runtime.composeApplicationContent([create.applicationContent, ...updates], runContext)
    : updates.reduce(
        (content, update) => ({ ...content, ...runtime.applyContext(update, runContext) }),
        create.applicationContent,
      );

  return { ...create, applicationContent };
}

function startApplicationDraft(context, runtime) {
  const dependencies = requireRuntime(runtime);
  const applicationDraft = composeDraft(dependencies, context.runContext);
  const response = dependencies.expectStatus(
    dependencies.request('POST', '/api/v0/application-drafts', applicationDraft, {
      headers: { 'Content-Type': 'application/json' },
    }),
    [201, 202],
  );
  const applicationDraftId = requireIdentifier(
    responseBody(response),
    'applicationId',
    'Start application draft',
  );
  context.applicationDraft = applicationDraft;
  context.applicationDraftId = applicationDraftId;
}

function applyApplicationDraftUpdate(sequence) {
  return (context, runtime) => {
    const dependencies = requireRuntime(runtime);
    const fixture = dependencies.applyContext(
      dependencies.loadFixture(`fixtures/application/update-${sequence}.json`),
      context.runContext,
    );
    context.applicationDraftUpdates = {
      ...(context.applicationDraftUpdates || {}),
      [sequence]: fixture,
    };
  };
}

function uploadApplicationDocument(context, runtime) {
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
}

function submitApplicationDraft(context, runtime) {
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
}

function recordManualGrantEligibility(context, runtime) {
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
}

function listApplications(context, runtime) {
  const dependencies = requireRuntime(runtime);
  const path = `/api/v0/applications?laaReference=${encodeURIComponent(context.runContext.laaReference)}`;
  const list = () => {
    const response = dependencies.expectStatus(dependencies.request('GET', path), 200);
    const application = responseBody(response).applications?.find(
      (candidate) => candidate.applicationId === context.applicationId && candidate.status === 'GRANTED',
    );
    return { response, application };
  };
  const result = dependencies.pollFor
    ? dependencies.pollFor('granted parent Application', () => {
        const attempt = list();
        return {
          matched: Boolean(attempt.application),
          retry: !attempt.application,
          value: attempt.application,
        };
      })
    : list().application;
  if (!result) {
    throw new Error('List applications did not return the granted parent Application');
  }
  context.grantedApplicationId = result.applicationId;
}

function startPriorAuthorityDraft(context, runtime) {
  const dependencies = requireRuntime(runtime);
  if (!context.grantedApplicationId) {
    throw new Error('Start Prior Authority draft requires grantedApplicationId');
  }
  const draft = dependencies.applyContext(
    dependencies.loadFixture('fixtures/prior-authority/create.json'),
    context.runContext,
  );
  draft.applicationId = context.grantedApplicationId;
  const response = dependencies.expectStatus(
    dependencies.request('POST', '/api/v0/prior-authorities', draft, {
      headers: { 'Content-Type': 'application/json' },
    }),
    [201, 202],
  );
  context.priorAuthorityId = requireIdentifier(
    responseBody(response),
    'priorAuthorityId',
    'Start Prior Authority draft',
  );
}

function updatePriorAuthorityDraft(context, runtime) {
  const dependencies = requireRuntime(runtime);
  if (!context.priorAuthorityId) {
    throw new Error('Update Prior Authority draft requires priorAuthorityId');
  }
  const update = dependencies.applyContext(
    dependencies.loadFixture('fixtures/prior-authority/update.json'),
    context.runContext,
  );
  dependencies.expectStatus(
    dependencies.request(
      'PUT',
      `/api/v0/prior-authorities/${context.priorAuthorityId}`,
      update,
      { headers: { 'Content-Type': 'application/json' } },
    ),
    204,
  );
}

function uploadPriorAuthorityDocument(context, runtime) {
  const dependencies = requireRuntime(runtime);
  if (!context.priorAuthorityId) {
    throw new Error('Upload Prior Authority document requires priorAuthorityId');
  }
  const response = dependencies.expectStatus(
    dependencies.request(
      'POST',
      `/api/v0/prior-authorities/${context.priorAuthorityId}/documents`,
      { file: dependencies.documentFixture('fixtures/documents/k6-evidence.pdf') },
    ),
    201,
  );
  context.priorAuthorityDocumentId = requireIdentifier(
    responseBody(response),
    'documentId',
    'Upload Prior Authority document',
  );
}

function submitPriorAuthority(context, runtime) {
  const dependencies = requireRuntime(runtime);
  if (!context.priorAuthorityId) {
    throw new Error('Submit Prior Authority requires priorAuthorityId');
  }
  const response = dependencies.expectStatus(
    dependencies.request(
      'POST',
      `/api/v0/prior-authorities/${context.priorAuthorityId}/submit`,
      null,
    ),
    [200, 202],
  );
  context.submittedPriorAuthorityId = requireIdentifier(
    responseBody(response),
    'priorAuthorityId',
    'Submit Prior Authority',
  );
}

function workListPath({ assigned, itemType }) {
  return assigned
    ? `/api/v0/work-list?assignedToMe=true&unassigned=false&itemType=${itemType}`
    : `/api/v0/work-list?unassigned=true&itemType=${itemType}`;
}

function findWorkListItem(context, runtime, {
  idKey,
  itemType,
  outputKey,
  assigned,
  description,
}) {
  const dependencies = requireRuntime(runtime);
  const itemId = context[idKey];
  if (!itemId) {
    throw new Error(`${description} requires ${idKey}`);
  }
  const find = () => {
    const response = dependencies.expectStatus(
      dependencies.request('GET', workListPath({ assigned, itemType })),
      200,
    );
    const item = responseBody(response).items?.find(
      (candidate) => candidate.itemId === itemId && candidate.itemType === itemType,
    );
    return { response, item };
  };
  const item = dependencies.pollFor
    ? dependencies.pollFor(description, () => {
        const attempt = find();
        return {
          matched: Boolean(attempt.item),
          retry: !attempt.item,
          value: attempt.item,
        };
      })
    : find().item;
  if (!item) {
    throw new Error(`${description} did not return ${itemType} ${itemId}`);
  }
  context[outputKey] = item;
}

function assignWorkListItem(context, runtime, {
  itemKey,
  outputKey,
  description,
}) {
  const dependencies = requireRuntime(runtime);
  const item = context[itemKey];
  if (!item?.itemId || item.assignmentVersion === undefined) {
    throw new Error(`${description} requires an item ID and assignment version`);
  }
  const assignment = dependencies.applyContext(
    dependencies.loadFixture('fixtures/work-list/assign.json'),
    context.runContext,
  );
  assignment.expectedAssignmentVersion = item.assignmentVersion;
  const response = dependencies.request(
    'POST',
    `/api/v0/work-list/${item.itemId}/assign`,
    assignment,
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (response.status === 409 && context.assignmentContention) {
    context[outputKey] = 'conflict';
    return;
  }
  dependencies.expectStatus(response, 200);
  context[outputKey] = 'assigned';
}

function verifyWorkListItemRemoved(context, runtime, { idKey, itemType, description }) {
  const dependencies = requireRuntime(runtime);
  const itemId = context[idKey];
  const verify = () => {
    const response = dependencies.expectStatus(
      dependencies.request('GET', workListPath({ assigned: false, itemType })),
      200,
    );
    const item = responseBody(response).items?.find(
      (candidate) => candidate.itemId === itemId && candidate.itemType === itemType,
    );
    return {
      matched: !item,
      retry: Boolean(item),
      value: !item,
    };
  };
  if (dependencies.pollFor) {
    dependencies.pollFor(description, verify);
  } else if (!verify().matched) {
    throw new Error(`${description} still contains ${itemType} ${itemId}`);
  }
}

function grantApplication(context, runtime) {
  const dependencies = requireRuntime(runtime);
  const decision = dependencies.applyContext(
    dependencies.loadFixture('fixtures/application/grant-decision.json'),
    context.runContext,
  );
  dependencies.expectStatus(
    dependencies.request(
      'PATCH',
      `/api/v0/applications/${context.applicationId}/decision`,
      decision,
      { headers: { 'Content-Type': 'application/json' } },
    ),
    200,
  );
  context.grantedApplicationId = context.applicationId;
  verifyWorkListItemRemoved(context, dependencies, {
    idKey: 'applicationId',
    itemType: 'APPLICATION',
    description: 'Application removal from Open Applications',
  });
}

function grantPriorAuthority(context, runtime) {
  const dependencies = requireRuntime(runtime);
  const decision = dependencies.applyContext(
    dependencies.loadFixture('fixtures/prior-authority/grant-decision.json'),
    context.runContext,
  );
  dependencies.expectStatus(
    dependencies.request(
      'PATCH',
      `/api/v0/prior-authorities/${context.submittedPriorAuthorityId}/decision`,
      decision,
      { headers: { 'Content-Type': 'application/json' } },
    ),
    204,
  );
  context.grantedPriorAuthorityId = context.submittedPriorAuthorityId;
  verifyWorkListItemRemoved(context, dependencies, {
    idKey: 'submittedPriorAuthorityId',
    itemType: 'PRIOR_AUTHORITY',
    description: 'Prior Authority removal from Open Applications',
  });
}

export function defineStep(metadata, execute) {
  REQUIRED_METADATA.forEach((field) => {
    if (metadata[field] === undefined || metadata[field] === null) {
      throw new Error(`Step ${metadata.canonicalName || 'metadata'} is missing ${field}`);
    }
  });

  return Object.freeze({ ...metadata, execute });
}

const applicationUpdates = Array.from({ length: 15 }, (_, index) => {
  const sequence = String(index + 1).padStart(2, '0');
  return defineStep({
    canonicalName: `Update application draft: ${sequence}`,
    actor: 'Provider',
    inputs: ['applicationDraft', `fixtures/application/update-${sequence}.json`],
    outputs: ['applicationDraft'],
    endpoint: 'Local fixture composition for POST /api/v0/application-drafts',
    assertion: 'The named fixture is recorded in the composed draft request.',
    tags: ['smoke', 'performance', 'application', 'draft'],
  }, applyApplicationDraftUpdate(sequence));
});

export const stepRegistry = Object.freeze([
  defineStep({
    canonicalName: 'Start application draft',
    actor: 'Provider',
    inputs: ['runContext', 'fixtures/application/create.json'],
    outputs: ['applicationDraftId'],
    endpoint: 'POST /api/v0/application-drafts',
    assertion: 'Returns HTTP 201 and an Application draft ID.',
    tags: ['smoke', 'performance', 'application', 'draft'],
  }, startApplicationDraft),
  ...applicationUpdates,
  defineStep({
    canonicalName: 'Upload application document',
    actor: 'Provider',
    inputs: ['applicationId', 'fixtures/documents/k6-evidence.pdf'],
    outputs: ['applicationDocumentId'],
    endpoint: 'POST /api/v0/applications/{id}/documents',
    assertion: 'Returns HTTP 201 and a document ID.',
    tags: ['smoke', 'performance', 'application', 'document'],
  }, uploadApplicationDocument),
  defineStep({
    canonicalName: 'Submit application draft',
    actor: 'Provider',
    inputs: ['applicationDraftId'],
    outputs: ['applicationId'],
    endpoint: 'POST /api/v0/application-drafts/{applicationId}/submit',
    assertion: 'Returns HTTP 200 or 202 and an Application ID.',
    tags: ['smoke', 'performance', 'application', 'submission'],
  }, submitApplicationDraft),
  defineStep({
    canonicalName: 'Record manual-grant eligibility',
    actor: 'Provider/system',
    inputs: ['applicationId', 'fixtures/application/manual-grant-outcome.json'],
    outputs: ['applicationVersion'],
    endpoint: 'PATCH /api/v0/applications/{id}/auto-grant-outcome',
    assertion: 'Returns HTTP 200 or 204 with a MANUAL outcome.',
    tags: ['smoke', 'performance', 'application', 'work-queue'],
  }, recordManualGrantEligibility),
  defineStep({
    canonicalName: 'Find application in Open Applications',
    actor: 'Application Caseworker',
    inputs: ['applicationId'],
    outputs: ['applicationWorkListItem'],
    endpoint: 'GET /api/v0/work-list?unassigned=true',
    assertion: 'The Application is visible as an unassigned Work Queue Item.',
    tags: ['smoke', 'contention', 'performance', 'work-queue'],
  }, (context, runtime) => findWorkListItem(context, runtime, {
    idKey: 'applicationId',
    itemType: 'APPLICATION',
    outputKey: 'applicationWorkListItem',
    assigned: false,
    description: 'Application in Open Applications',
  })),
  defineStep({
    canonicalName: 'Assign application from Work Queue',
    actor: 'Application Caseworker',
    inputs: ['applicationWorkListItem', 'fixtures/work-list/assign.json'],
    outputs: ['applicationAssignment'],
    endpoint: 'POST /api/v0/work-list/{itemId}/assign',
    assertion: 'Returns HTTP 200 for the expected assignment version.',
    tags: ['smoke', 'contention', 'performance', 'work-queue'],
  }, (context, runtime) => assignWorkListItem(context, runtime, {
    itemKey: 'applicationWorkListItem',
    outputKey: 'applicationAssignment',
    description: 'Application assignment',
  })),
  defineStep({
    canonicalName: 'Find application in Personal Queue',
    actor: 'Application Caseworker',
    inputs: ['applicationId'],
    outputs: ['assignedApplicationWorkListItem'],
    endpoint: 'GET /api/v0/work-list?assignedToMe=true&unassigned=false',
    assertion: 'The Application is visible in the caseworker Personal Queue.',
    tags: ['smoke', 'performance', 'work-queue'],
  }, (context, runtime) => findWorkListItem(context, runtime, {
    idKey: 'applicationId',
    itemType: 'APPLICATION',
    outputKey: 'assignedApplicationWorkListItem',
    assigned: true,
    description: 'Application in Personal Queue',
  })),
  defineStep({
    canonicalName: 'Grant application',
    actor: 'Application Caseworker',
    inputs: ['applicationId', 'fixtures/application/grant-decision.json'],
    outputs: ['grantedApplicationId'],
    endpoint: 'PATCH /api/v0/applications/{id}/decision',
    assertion: 'Returns HTTP 200 and removes the Application from the Work Queue.',
    tags: ['smoke', 'performance', 'application', 'decision'],
  }, grantApplication),
  defineStep({
    canonicalName: 'List applications',
    actor: 'Provider',
    inputs: ['laaReference'],
    outputs: ['grantedApplicationId'],
    endpoint: 'GET /api/v0/applications',
    assertion: 'The granted parent Application is discoverable.',
    tags: ['smoke', 'performance', 'application'],
  }, listApplications),
  defineStep({
    canonicalName: 'Start Prior Authority draft',
    actor: 'Provider',
    inputs: ['grantedApplicationId', 'fixtures/prior-authority/create.json'],
    outputs: ['priorAuthorityId'],
    endpoint: 'POST /api/v0/prior-authorities',
    assertion: 'Returns HTTP 201 or 202 and a Prior Authority ID.',
    tags: ['smoke', 'performance', 'prior-authority', 'draft'],
  }, startPriorAuthorityDraft),
  defineStep({
    canonicalName: 'Update Prior Authority draft',
    actor: 'Provider',
    inputs: ['priorAuthorityId', 'fixtures/prior-authority/update.json'],
    outputs: ['priorAuthorityId'],
    endpoint: 'PUT /api/v0/prior-authorities/{priorAuthorityId}',
    assertion: 'Returns HTTP 204 after saving the required data.',
    tags: ['smoke', 'performance', 'prior-authority', 'draft'],
  }, updatePriorAuthorityDraft),
  defineStep({
    canonicalName: 'Upload Prior Authority document',
    actor: 'Provider',
    inputs: ['priorAuthorityId', 'fixtures/documents/k6-evidence.pdf'],
    outputs: ['priorAuthorityDocumentId'],
    endpoint: 'POST /api/v0/prior-authorities/{priorAuthorityId}/documents',
    assertion: 'Returns HTTP 201 and a document ID.',
    tags: ['smoke', 'performance', 'prior-authority', 'document'],
  }, uploadPriorAuthorityDocument),
  defineStep({
    canonicalName: 'Submit Prior Authority',
    actor: 'Provider',
    inputs: ['priorAuthorityId'],
    outputs: ['submittedPriorAuthorityId'],
    endpoint: 'POST /api/v0/prior-authorities/{priorAuthorityId}/submit',
    assertion: 'Returns HTTP 200 or 202.',
    tags: ['smoke', 'performance', 'prior-authority', 'submission'],
  }, submitPriorAuthority),
  defineStep({
    canonicalName: 'Find PA in Open Applications',
    actor: 'PA Caseworker',
    inputs: ['submittedPriorAuthorityId'],
    outputs: ['priorAuthorityWorkListItem'],
    endpoint: 'GET /api/v0/work-list?unassigned=true',
    assertion: 'The Prior Authority is visible as an unassigned Work Queue Item.',
    tags: ['smoke', 'performance', 'work-queue', 'prior-authority'],
  }, (context, runtime) => findWorkListItem(context, runtime, {
    idKey: 'submittedPriorAuthorityId',
    itemType: 'PRIOR_AUTHORITY',
    outputKey: 'priorAuthorityWorkListItem',
    assigned: false,
    description: 'Prior Authority in Open Applications',
  })),
  defineStep({
    canonicalName: 'Assign PA from Work Queue',
    actor: 'PA Caseworker',
    inputs: ['priorAuthorityWorkListItem', 'fixtures/work-list/assign.json'],
    outputs: ['priorAuthorityAssignment'],
    endpoint: 'POST /api/v0/work-list/{itemId}/assign',
    assertion: 'Returns HTTP 200 for the expected assignment version.',
    tags: ['smoke', 'performance', 'work-queue', 'prior-authority'],
  }, (context, runtime) => assignWorkListItem(context, runtime, {
    itemKey: 'priorAuthorityWorkListItem',
    outputKey: 'priorAuthorityAssignment',
    description: 'Prior Authority assignment',
  })),
  defineStep({
    canonicalName: 'Find PA in Personal Queue',
    actor: 'PA Caseworker',
    inputs: ['submittedPriorAuthorityId'],
    outputs: ['assignedPriorAuthorityWorkListItem'],
    endpoint: 'GET /api/v0/work-list?assignedToMe=true&unassigned=false',
    assertion: 'The Prior Authority is visible in the caseworker Personal Queue.',
    tags: ['smoke', 'performance', 'work-queue', 'prior-authority'],
  }, (context, runtime) => findWorkListItem(context, runtime, {
    idKey: 'submittedPriorAuthorityId',
    itemType: 'PRIOR_AUTHORITY',
    outputKey: 'assignedPriorAuthorityWorkListItem',
    assigned: true,
    description: 'Prior Authority in Personal Queue',
  })),
  defineStep({
    canonicalName: 'Grant PA',
    actor: 'PA Caseworker',
    inputs: ['submittedPriorAuthorityId', 'fixtures/prior-authority/grant-decision.json'],
    outputs: ['grantedPriorAuthorityId'],
    endpoint: 'PATCH /api/v0/prior-authorities/{priorAuthorityId}/decision',
    assertion: 'Returns HTTP 204 and removes the Prior Authority from the Work Queue.',
    tags: ['smoke', 'performance', 'prior-authority', 'decision'],
  }, grantPriorAuthority),
]);
