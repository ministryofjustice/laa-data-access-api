import { defineStep } from '../define-step.js';
import { uploadDocument } from './actions.js';

export const uploadPriorAuthorityDocument = defineStep({ canonicalName: 'Upload Prior Authority document', actor: 'Provider', inputs: ['priorAuthorityId', 'fixtures/documents/k6-evidence.pdf'], outputs: ['priorAuthorityDocumentId'], endpoint: 'POST /api/v0/prior-authorities/{priorAuthorityId}/documents', assertion: 'Returns HTTP 201 and a document ID.', tags: ['smoke', 'performance', 'prior-authority', 'document'] }, uploadDocument);
