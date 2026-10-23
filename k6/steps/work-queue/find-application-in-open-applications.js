import { defineStep } from '../define-step.js';
import { find } from './actions.js';

export const findApplicationInOpenApplications = defineStep({ canonicalName: 'Find application in Open Applications', actor: 'Application Caseworker', inputs: ['applicationId'], outputs: ['applicationWorkListItem'], endpoint: 'GET /api/v0/work-list?unassigned=true', assertion: 'The Application is visible as an unassigned Work Queue Item.', tags: ['smoke', 'contention', 'performance', 'work-queue'] }, find({ idKey: 'applicationId', itemType: 'APPLICATION', outputKey: 'applicationWorkListItem', assigned: false, description: 'Application in Open Applications' }));
