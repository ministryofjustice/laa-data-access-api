// Custom handleSummary: writes
//   - stdout text summary (human-readable end-of-run output)
//   - JSON file (full metric set for archival / further processing)
//   - JUnit XML (one testcase per threshold — consumable by CI test reporters)

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import { env } from '../config/env.js';

const escapeXml = (str) =>
  String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');

// k6's threshold results live under data.metrics[metricName].thresholds.
// Each threshold entry has { ok: boolean } plus the expression. We flatten
// them into one testcase per threshold so a JUnit reporter shows a row per SLO.
const buildJUnit = (data) => {
  const cases = [];
  let failures = 0;

  for (const [metricName, metric] of Object.entries(data.metrics || {})) {
    if (!metric.thresholds) continue;
    for (const [expr, result] of Object.entries(metric.thresholds)) {
      const passed = result.ok !== false;
      if (!passed) failures += 1;
      const name = escapeXml(`${metricName} ${expr}`);
      if (passed) {
        cases.push(`    <testcase classname="thresholds" name="${name}" />`);
      } else {
        cases.push(
          `    <testcase classname="thresholds" name="${name}">\n` +
            `      <failure message="${name} breached" />\n` +
            `    </testcase>`,
        );
      }
    }
  }

  const total = cases.length;
  return (
    `<?xml version="1.0" encoding="UTF-8"?>\n` +
    `<testsuites>\n` +
    `  <testsuite name="k6" tests="${total}" failures="${failures}">\n` +
    `${cases.join('\n')}\n` +
    `  </testsuite>\n` +
    `</testsuites>\n`
  );
};

export const handleSummary = (data) => ({
  stdout: textSummary(data, { indent: ' ', enableColors: true }),
  [env.summaryOut]: JSON.stringify(data, null, 2),
  [env.junitOut]: buildJUnit(data),
});
