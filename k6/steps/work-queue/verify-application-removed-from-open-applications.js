import { defineStep } from '../define-step.js';
import { verifyRemoved } from './actions.js';

export const verifyApplicationRemovedFromOpenApplications = defineStep({ canonicalName: 'Verify application removed from Open Applications', actor: 'Application Caseworker', inputs: ['applicationId'], outputs: ['applicationRemovedFromOpenApplications'], endpoint: 'GET /api/v0/work-list?unassigned=true', assertion: 'The granted Application is no longer visible in Open Applications.', tags: ['smoke', 'performance', 'application', 'work-queue'] }, verifyRemoved({ idKey: 'applicationId', itemType: 'APPLICATION', description: 'Application removal from Open Applications' }, 'applicationRemovedFromOpenApplications'));
