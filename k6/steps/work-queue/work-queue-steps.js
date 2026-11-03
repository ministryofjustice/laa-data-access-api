import { findApplicationInOpenApplications } from './find-application-in-open-applications.js';
import { assignApplicationFromWorkQueue } from './assign-application-from-work-queue.js';
import { findApplicationInPersonalQueue } from './find-application-in-personal-queue.js';
import { grantApplication } from './grant-application.js';
import { verifyApplicationRemovedFromOpenApplications } from './verify-application-removed-from-open-applications.js';
import { findPriorAuthorityInOpenApplications } from './find-pa-in-open-applications.js';
import { assignPriorAuthorityFromWorkQueue } from './assign-pa-from-work-queue.js';
import { findPriorAuthorityInPersonalQueue } from './find-pa-in-personal-queue.js';
import { grantPriorAuthority } from './grant-pa.js';
import { verifyPriorAuthorityRemovedFromOpenApplications } from './verify-pa-removed-from-open-applications.js';

export const workQueueSteps = Object.freeze([
  findApplicationInOpenApplications,
  assignApplicationFromWorkQueue,
  findApplicationInPersonalQueue,
  grantApplication,
  verifyApplicationRemovedFromOpenApplications,
  findPriorAuthorityInOpenApplications,
  assignPriorAuthorityFromWorkQueue,
  findPriorAuthorityInPersonalQueue,
  grantPriorAuthority,
  verifyPriorAuthorityRemovedFromOpenApplications,
]);
