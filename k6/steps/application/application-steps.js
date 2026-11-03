import { startApplicationDraft } from './start-application-draft.js';
import { uploadApplicationDocument } from './upload-application-document.js';
import { submitApplicationDraft } from './submit-application-draft.js';
import { recordManualGrantEligibility } from './record-manual-grant-eligibility.js';
import { listApplications } from './list-applications.js';

export const applicationSteps = Object.freeze([
  startApplicationDraft,
  uploadApplicationDocument,
  submitApplicationDraft,
  recordManualGrantEligibility,
  listApplications,
]);
