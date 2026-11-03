import { defineStep } from '../define-step.js';
import { find } from './actions.js';

export const findApplicationInPersonalQueue = defineStep({ canonicalName: 'Find application in Personal Queue', actor: 'Application Caseworker', inputs: ['applicationId'], outputs: ['assignedApplicationWorkListItem'], endpoint: 'GET /api/v0/work-list?assignedToMe=true&unassigned=false', assertion: 'The Application is visible in the caseworker Personal Queue.', tags: ['smoke', 'performance', 'work-queue'] }, find({ idKey: 'applicationId', itemType: 'APPLICATION', outputKey: 'assignedApplicationWorkListItem', assigned: true, description: 'Application in Personal Queue' }));
