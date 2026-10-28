import { defineStep } from '../define-step.js';
import { assign } from './actions.js';

export const assignApplicationFromWorkQueue = defineStep({ canonicalName: 'Assign application from Work Queue', actor: 'Application Caseworker', inputs: ['applicationWorkListItem', 'fixtures/work-list/assign.json'], outputs: ['applicationAssignment'], endpoint: 'POST /api/v0/work-list/{itemId}/assign', assertion: 'Returns HTTP 200 for the expected assignment version.', tags: ['smoke', 'contention', 'performance', 'work-queue'] }, assign({ itemKey: 'applicationWorkListItem', outputKey: 'applicationAssignment', description: 'Application assignment' }));
