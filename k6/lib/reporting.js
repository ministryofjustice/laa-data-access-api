import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

export function summary(config) {
  return (data) => ({
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [config.summaryPath]: JSON.stringify(data),
  });
}
