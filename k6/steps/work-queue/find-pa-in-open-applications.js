import { defineStep } from '../define-step.js';
import { find } from './actions.js';

export const findPriorAuthorityInOpenApplications = defineStep({ canonicalName: 'Find PA in Open Applications', actor: 'PA Caseworker', inputs: ['submittedPriorAuthorityId'], outputs: ['priorAuthorityWorkListItem'], endpoint: 'GET /api/v0/work-list?unassigned=true', assertion: 'The Prior Authority is visible as an unassigned Work Queue Item.', tags: ['smoke', 'performance', 'work-queue', 'prior-authority'] }, find({ idKey: 'submittedPriorAuthorityId', itemType: 'PRIOR_AUTHORITY', outputKey: 'priorAuthorityWorkListItem', assigned: false, description: 'Prior Authority in Open Applications' }));
