// Wraps k6's `check` with tag plumbing so every assertion shows up under the
// owning request name in summaries and Prometheus.

import { check } from 'k6';

export const expectStatus = (name, res, expected = 200) =>
  check(res, { [`${name} status is ${expected}`]: (r) => r.status === expected }, { name });

export const expectJsonBody = (name, res) =>
  check(
    res,
    {
      [`${name} returns JSON`]: (r) => {
        try {
          JSON.parse(r.body);
          return true;
        } catch (_) {
          return false;
        }
      },
    },
    { name },
  );

export const expectHeader = (name, res, header) =>
  check(
    res,
    { [`${name} has ${header} header`]: (r) => r.headers[header] !== undefined },
    { name },
  );
