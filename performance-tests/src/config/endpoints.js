// Endpoint definitions for the data-access-api surface.
//
// Tests reference these by key so a URL change is a one-line update here
// instead of a sweep across every test file.

import { env } from './env.js';

const base = () => env.targetUrl.replace(/\/$/, '');

export const endpoints = {
  getApplications: () => `${base()}/api/v0/applications`,
  getApplicationById: (id) => `${base()}/api/v0/applications/${id}`,
  postApplication: () => `${base()}/api/v0/applications`,
  patchApplicationDecision: (id) => `${base()}/api/v0/applications/${id}/decision`,
};
