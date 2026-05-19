// Auth header construction.
//
// Cluster runs receive K6_TOKEN from the K6 Operator TestRun secret;
// local runs fall back to the dev dummy token in env.js.

import { env } from '../config/env.js';

export const authHeaders = () => ({
  Authorization: `Bearer ${env.token}`,
  'X-Service-Name': env.serviceName,
  Accept: 'application/json',
});
