import { defineStep } from '../define-step.js';
import { submit } from './actions.js';

export const submitPriorAuthority = defineStep({ canonicalName: 'Submit Prior Authority', actor: 'Provider', inputs: ['priorAuthorityId'], outputs: ['submittedPriorAuthorityId'], endpoint: 'POST /api/v0/prior-authorities/{priorAuthorityId}/submit', assertion: 'Returns HTTP 200 or 202.', tags: ['smoke', 'performance', 'prior-authority', 'submission'] }, submit);
