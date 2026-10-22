import assert from 'node:assert/strict';
import test from 'node:test';

import { validateDocumentation } from '../scripts/validate-documentation.js';

const registeredStep = {
  canonicalName: 'Start application draft',
  actor: 'Provider',
  inputs: [],
  outputs: ['applicationDraftId'],
  endpoint: 'POST /api/v0/application-drafts',
  assertion: 'Returns an Application draft ID',
  tags: ['smoke', 'application'],
};

test('validation reports feature steps missing from the registry', () => {
  assert.throws(
    () =>
      validateDocumentation({
        registry: [registeredStep],
        catalogue: '## Start application draft\n',
        features: ['Feature: journey\n  Given Unknown fixture step\n'],
      }),
    /Unknown fixture step/,
  );
});

test('validation reports registered steps missing from the catalogue', () => {
  assert.throws(
    () =>
      validateDocumentation({
        registry: [registeredStep],
        catalogue: '# k6 step catalogue\n',
        features: ['Feature: journey\n  Given Start application draft\n'],
      }),
    /Start application draft/,
  );
});
