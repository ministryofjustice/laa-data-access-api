import { defineStep } from '../define-step.js';
import { requireRuntime, responseBody } from '../step-support.js';

export const listApplications = defineStep({
  canonicalName: 'List applications',
  actor: 'Provider',
  inputs: ['laaReference'],
  outputs: ['grantedApplicationId'],
  endpoint: 'GET /api/v0/applications',
  assertion: 'The granted parent Application is discoverable.',
  tags: ['smoke', 'performance', 'application'],
}, (context, runtime) => {
  const dependencies = requireRuntime(runtime);
  const path = `/api/v0/applications?laaReference=${encodeURIComponent(context.runContext.laaReference)}`;
  const list = () => {
    const response = dependencies.expectStatus(dependencies.request('GET', path), 200);
    const application = responseBody(response).applications?.find(
      (candidate) => candidate.applicationId === context.applicationId && candidate.status === 'GRANTED',
    );
    return { application };
  };
  const result = dependencies.pollFor
    ? dependencies.pollFor('granted parent Application', () => {
        const attempt = list();
        return {
          matched: Boolean(attempt.application),
          retry: !attempt.application,
          value: attempt.application,
        };
      })
    : list().application;
  if (!result) {
    throw new Error('List applications did not return the granted parent Application');
  }
  context.grantedApplicationId = result.applicationId;
});
