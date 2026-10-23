import { defineStep } from '../define-step.js';
import { updateDocumentType } from './actions.js';

export const updatePriorAuthorityDocumentType = defineStep({
  canonicalName: 'Update Prior Authority document type',
  actor: 'Provider',
  inputs: [
    'priorAuthorityId',
    'priorAuthorityDocumentId',
    'fixtures/prior-authority/document-type.json',
  ],
  outputs: ['priorAuthorityDocumentId'],
  endpoint: 'PATCH /api/v0/prior-authorities/{priorAuthorityId}/documents/{documentId}',
  assertion: 'Returns HTTP 200 after assigning the document type.',
  tags: ['smoke', 'performance', 'prior-authority', 'document'],
}, updateDocumentType);
