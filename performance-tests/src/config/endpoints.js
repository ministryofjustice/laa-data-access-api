// Endpoint definitions for the data-access-api surface.
//
// Tests reference these by key so a URL change is a one-line update here
// instead of a sweep across every test file.

import { env } from './env.js';

const base = () => env.targetUrl.replace(/\/$/, '');

// Encodes a flat params object into a query string. Array values repeat the key
// (e.g. {tag:['a','b']} → ?tag=a&tag=b). null/undefined values are dropped.
const qs = (params) => {
  if (!params) return '';
  const pairs = [];
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    const encodedKey = encodeURIComponent(key);
    if (Array.isArray(value)) {
      value.forEach((v) => pairs.push(`${encodedKey}=${encodeURIComponent(v)}`));
    } else {
      pairs.push(`${encodedKey}=${encodeURIComponent(value)}`);
    }
  }
  return pairs.length === 0 ? '' : `?${pairs.join('&')}`;
};

export const endpoints = {
  getApplications: (params) => `${base()}/api/v0/applications${qs(params)}`,
  getApplicationById: (id, params) => `${base()}/api/v0/applications/${id}${qs(params)}`,
  postApplication: () => `${base()}/api/v0/applications`,
  patchApplicationDecision: (id) => `${base()}/api/v0/applications/${id}/decision`,
};
