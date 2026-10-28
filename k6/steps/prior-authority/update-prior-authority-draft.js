import { defineStep } from '../define-step.js';
import { update } from './actions.js';

export const updatePriorAuthorityDraft = defineStep({ canonicalName: 'Update Prior Authority draft', actor: 'Provider', inputs: ['priorAuthorityId', 'fixtures/prior-authority/update.json'], outputs: ['priorAuthorityId'], endpoint: 'PUT /api/v0/prior-authorities/{priorAuthorityId}', assertion: 'Returns HTTP 204 after saving the required data.', tags: ['smoke', 'performance', 'prior-authority', 'draft'] }, update);
