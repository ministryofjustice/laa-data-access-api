import { startPriorAuthorityDraft } from './start-prior-authority-draft.js';
import { updatePriorAuthorityDraft } from './update-prior-authority-draft.js';
import { uploadPriorAuthorityDocument } from './upload-prior-authority-document.js';
import { updatePriorAuthorityDocumentType } from './update-prior-authority-document-type.js';
import { submitPriorAuthority } from './submit-prior-authority.js';

export const priorAuthoritySteps = Object.freeze([
  startPriorAuthorityDraft,
  updatePriorAuthorityDraft,
  uploadPriorAuthorityDocument,
  updatePriorAuthorityDocumentType,
  submitPriorAuthority,
]);
