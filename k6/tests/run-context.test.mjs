import assert from 'node:assert/strict';
import test from 'node:test';

import {
  applyRunContext,
  createRunContext,
} from '../lib/run-context.js';

test('run contexts give concurrent virtual users distinct visible identifiers', () => {
  const first = createRunContext({ runId: 'catalogue', vu: 1, iteration: 2 });
  const second = createRunContext({ runId: 'catalogue', vu: 2, iteration: 2 });

  assert.notEqual(first.applicationId, second.applicationId);
  assert.notEqual(first.priorAuthorityId, second.priorAuthorityId);
  assert.notEqual(first.clientId, second.clientId);
  assert.notEqual(first.laaReference, second.laaReference);
  assert.match(first.clientId, /^k6-catalogue-vu1-it2-client$/);
  assert.match(first.laaReference, /^LAA-K6-CATALOGUE-VU1-IT2$/);
});

test('run contexts substitute every visible identifier in fixture templates', () => {
  const context = createRunContext({ runId: 'catalogue', vu: 1, iteration: 2 });

  assert.deepEqual(
    applyRunContext(
      {
        id: '{{applicationId}}',
        client: '{{clientId}}',
        reference: '{{laaReference}}',
      },
      context,
    ),
    {
      id: context.applicationId,
      client: context.clientId,
      reference: context.laaReference,
    },
  );
});
