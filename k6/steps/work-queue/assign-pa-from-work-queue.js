import { defineStep } from '../define-step.js';
import { assign } from './actions.js';

export const assignPriorAuthorityFromWorkQueue = defineStep({ canonicalName: 'Assign PA from Work Queue', actor: 'PA Caseworker', inputs: ['priorAuthorityWorkListItem', 'fixtures/work-list/assign.json'], outputs: ['priorAuthorityAssignment'], endpoint: 'POST /api/v0/work-list/{itemId}/assign', assertion: 'Returns HTTP 200 for the expected assignment version.', tags: ['smoke', 'performance', 'work-queue', 'prior-authority'] }, assign({ itemKey: 'priorAuthorityWorkListItem', outputKey: 'priorAuthorityAssignment', description: 'Prior Authority assignment' }));
