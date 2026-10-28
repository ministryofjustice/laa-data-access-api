import { defineStep } from '../define-step.js';
import { start } from './actions.js';

export const startPriorAuthorityDraft = defineStep({ canonicalName: 'Start Prior Authority draft', actor: 'Provider', inputs: ['grantedApplicationId', 'fixtures/prior-authority/create.json'], outputs: ['priorAuthorityId'], endpoint: 'POST /api/v0/prior-authorities', assertion: 'Returns HTTP 201 or 202 and a Prior Authority ID.', tags: ['smoke', 'performance', 'prior-authority', 'draft'] }, start);
