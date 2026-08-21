// Default per-endpoint thresholds.
//
// A test fails (CI/cluster run flagged red) if any of these breach. Override
// per scenario or per test by spreading into your `options.thresholds`.

export const defaultThresholds = {
  // Global health: <1% requests should fail.
  http_req_failed: ['rate<0.01'],

  // Global latency budget — generous enough to cover any endpoint.
  http_req_duration: ['p(95)<2000', 'p(99)<5000'],

  // No iteration should throw an unhandled error.
  checks: ['rate>0.99'],
};

// Per-endpoint overrides keyed by request tag. Spread alongside defaultThresholds
// when a specific endpoint has tighter or looser SLOs than the global default.
export const endpointThresholds = {
  'http_req_duration{name:GET /api/v0/applications}': ['p(95)<800', 'p(99)<1500'],
  'http_req_failed{name:GET /api/v0/applications}': ['rate<0.01'],
};
