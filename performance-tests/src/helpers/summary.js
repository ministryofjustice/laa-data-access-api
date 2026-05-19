// Custom handleSummary: writes stdout (k6's textSummary) AND a JSON file
// so cluster runs can ship the artifact off-pod for archival.

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import { env } from '../config/env.js';

export const handleSummary = (data) => ({
  stdout: textSummary(data, { indent: ' ', enableColors: true }),
  [env.summaryOut]: JSON.stringify(data, null, 2),
});
