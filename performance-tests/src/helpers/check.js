// Wraps k6's `check` with tag plumbing so every assertion shows up under the
// owning request name in summaries and Prometheus.

import { check } from 'k6';

const tagged = (name) => ({ name });

const parseBody = (res) => {
  try {
    return JSON.parse(res.body);
  } catch (_) {
    return null;
  }
};

// Resolves a dot-path like "page.totalElements" against a parsed body.
const dig = (obj, path) =>
  path.split('.').reduce((acc, key) => (acc == null ? undefined : acc[key]), obj);

// Comparator strings supported by expectArrayLength.
const compare = (actual, expected) => {
  const m = expected.match(/^([<>]=?|=)\s*(\d+)$/);
  if (!m) return actual === parseInt(expected, 10);
  const target = parseInt(m[2], 10);
  switch (m[1]) {
    case '>':  return actual > target;
    case '>=': return actual >= target;
    case '<':  return actual < target;
    case '<=': return actual <= target;
    case '=':  return actual === target;
    default:   return false;
  }
};

export const expectStatus = (name, res, expected = 200) =>
  check(res, { [`${name} status is ${expected}`]: (r) => r.status === expected }, tagged(name));

export const expectJsonBody = (name, res) =>
  check(res, { [`${name} returns JSON`]: (r) => parseBody(r) !== null }, tagged(name));

export const expectHeader = (name, res, header) =>
  check(res, { [`${name} has ${header} header`]: (r) => r.headers[header] !== undefined }, tagged(name));

// Assert a field exists at the given dot-path in the JSON body.
export const expectField = (name, res, path) =>
  check(
    res,
    { [`${name} has field ${path}`]: (r) => dig(parseBody(r), path) !== undefined },
    tagged(name),
  );

// Assert an array field length matches the comparator (e.g. ">0", ">=5", "=10").
export const expectArrayLength = (name, res, path, comparator) =>
  check(
    res,
    {
      [`${name} array ${path} ${comparator}`]: (r) => {
        const value = dig(parseBody(r), path);
        return Array.isArray(value) && compare(value.length, comparator);
      },
    },
    tagged(name),
  );

// Per-request hard cap on response time. Useful when global thresholds are too loose
// for a specific call (e.g. a cached endpoint that should always return <100ms).
export const expectResponseTime = (name, res, maxMs) =>
  check(
    res,
    { [`${name} responded in <${maxMs}ms`]: (r) => r.timings.duration < maxMs },
    tagged(name),
  );
