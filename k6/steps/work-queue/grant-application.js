import { defineStep } from '../define-step.js';
import { grant } from './actions.js';

export const grantApplication = defineStep({ canonicalName: 'Grant application', actor: 'Application Caseworker', inputs: ['applicationId', 'fixtures/application/grant-decision.json'], outputs: ['grantedApplicationId'], endpoint: 'PATCH /api/v0/applications/{id}/decision', assertion: 'Returns HTTP 200 or 204 and records the granted Application.', tags: ['smoke', 'performance', 'application', 'decision'] }, grant({ path: (context) => `/api/v0/applications/${context.applicationId}/decision`, fixture: 'fixtures/application/grant-decision.json', contextKey: 'applicationId', outputKey: 'grantedApplicationId', expectedStatus: [200, 204] }));
