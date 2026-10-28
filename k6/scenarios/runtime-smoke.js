import { check } from 'k6';
import { fetchTokens } from '../lib/auth.js';
import { loadConfig } from '../lib/config.js';
import { request } from '../lib/http.js';
import { summary } from '../lib/reporting.js';

const config = loadConfig(__ENV, ['provider']);

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: config.thresholds,
};

export function setup() {
  return fetchTokens(config, Object.keys(config.actors));
}

export default function (tokens) {
  const response = request(config, tokens.provider, 'GET', '/actuator/health');
  check(response, {
    'health endpoint is reachable': (result) => result.status >= 200 && result.status < 500,
  });
}

export const handleSummary = summary(config);
