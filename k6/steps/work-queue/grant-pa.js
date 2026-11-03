import { defineStep } from '../define-step.js';
import { grant } from './actions.js';

export const grantPriorAuthority = defineStep({ canonicalName: 'Grant PA', actor: 'PA Caseworker', inputs: ['submittedPriorAuthorityId', 'fixtures/prior-authority/grant-decision.json'], outputs: ['grantedPriorAuthorityId'], endpoint: 'PATCH /api/v0/prior-authorities/{priorAuthorityId}/decision', assertion: 'Returns HTTP 204 and records the granted Prior Authority.', tags: ['smoke', 'performance', 'prior-authority', 'decision'] }, grant({ path: (context) => `/api/v0/prior-authorities/${context.submittedPriorAuthorityId}/decision`, fixture: 'fixtures/prior-authority/grant-decision.json', contextKey: 'submittedPriorAuthorityId', outputKey: 'grantedPriorAuthorityId', expectedStatus: 204 }));
