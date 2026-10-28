import { applicationSteps } from '../steps/application/application-steps.js';
import { priorAuthoritySteps } from '../steps/prior-authority/prior-authority-steps.js';
import { workQueueSteps } from '../steps/work-queue/work-queue-steps.js';

export const stepRegistry = Object.freeze([
  ...applicationSteps,
  ...priorAuthoritySteps,
  ...workQueueSteps,
]);
