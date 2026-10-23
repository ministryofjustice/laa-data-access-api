export function requireRuntime(runtime) {
  if (!runtime || typeof runtime.loadFixture !== 'function' || typeof runtime.request !== 'function') {
    throw new Error('Provider Application steps require fixture loading and HTTP request functions');
  }
  return runtime;
}

export function responseBody(response) {
  if (!response || typeof response.json !== 'function') {
    throw new Error('Expected a JSON HTTP response');
  }
  return response.json();
}

export function requireIdentifier(body, key, step) {
  if (!body || !body[key]) {
    throw new Error(`${step} response did not contain ${key}`);
  }
  return body[key];
}

function workListPath({ assigned, itemType }) {
  return assigned
    ? `/api/v0/work-list?assignedToMe=true&unassigned=false&itemType=${itemType}`
    : `/api/v0/work-list?unassigned=true&itemType=${itemType}`;
}

export function findWorkListItem(context, runtime, {
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

export function assignWorkListItem(context, runtime, {
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

export function verifyWorkListItemRemoved(context, runtime, { idKey, itemType, description }) {
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
