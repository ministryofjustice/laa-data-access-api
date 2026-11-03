import { defineStep } from '../define-step.js';
import { find } from './actions.js';

export const findPriorAuthorityInPersonalQueue = defineStep({ canonicalName: 'Find PA in Personal Queue', actor: 'PA Caseworker', inputs: ['submittedPriorAuthorityId'], outputs: ['assignedPriorAuthorityWorkListItem'], endpoint: 'GET /api/v0/work-list?assignedToMe=true&unassigned=false', assertion: 'The Prior Authority is visible in the caseworker Personal Queue.', tags: ['smoke', 'performance', 'work-queue', 'prior-authority'] }, find({ idKey: 'submittedPriorAuthorityId', itemType: 'PRIOR_AUTHORITY', outputKey: 'assignedPriorAuthorityWorkListItem', assigned: true, description: 'Prior Authority in Personal Queue' }));
