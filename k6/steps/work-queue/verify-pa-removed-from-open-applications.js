import { defineStep } from '../define-step.js';
import { verifyRemoved } from './actions.js';

export const verifyPriorAuthorityRemovedFromOpenApplications = defineStep({ canonicalName: 'Verify PA removed from Open Applications', actor: 'PA Caseworker', inputs: ['submittedPriorAuthorityId'], outputs: ['priorAuthorityRemovedFromOpenApplications'], endpoint: 'GET /api/v0/work-list?unassigned=true', assertion: 'The granted Prior Authority is no longer visible in Open Applications.', tags: ['smoke', 'performance', 'prior-authority', 'work-queue'] }, verifyRemoved({ idKey: 'submittedPriorAuthorityId', itemType: 'PRIOR_AUTHORITY', description: 'Prior Authority removal from Open Applications' }, 'priorAuthorityRemovedFromOpenApplications'));
