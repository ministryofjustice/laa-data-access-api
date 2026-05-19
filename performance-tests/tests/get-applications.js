// Performance test: GET /api/v0/applications (happy path).
//
// Single endpoint, returns a paginated list of application summaries.
// Adjust the scenario via `K6_SCENARIO=load` (or smoke/stress/soak/spike).

import { sleep } from 'k6';
import { endpoints } from '../src/config/endpoints.js';
import { defaultThresholds, endpointThresholds } from '../src/config/thresholds.js';
import { get } from '../src/client/http.js';
import { selectScenario } from '../src/scenarios/index.js';
import { expectStatus, expectJsonBody } from '../src/helpers/check.js';
import { handleSummary } from '../src/helpers/summary.js';

const REQUEST_NAME = 'GET /api/v0/applications';

export const options = {
  scenarios: selectScenario(),
  thresholds: { ...defaultThresholds, ...endpointThresholds },
  // Tag every metric with the test name so dashboards can filter by test.
  tags: { test: 'get-applications' },
};

export default function () {
  const res = get(REQUEST_NAME, endpoints.getApplications());

  expectStatus(REQUEST_NAME, res, 200);
  expectJsonBody(REQUEST_NAME, res);

  // Small think-time so we don't hammer the API with a tight loop.
  sleep(1);
}

export { handleSummary };
