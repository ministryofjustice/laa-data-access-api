import http from 'k6/http';

import { expectStatus, request } from '../lib/http.js';
import { pollFor } from '../lib/polling.js';
import { applyRunContext } from '../lib/run-context.js';

const fixtures = Object.freeze({
  'fixtures/application/create.json': JSON.parse(open('../fixtures/application/create.json')),
  'fixtures/application/grant-decision.json': JSON.parse(
    open('../fixtures/application/grant-decision.json'),
  ),
  'fixtures/application/manual-grant-outcome.json': JSON.parse(
    open('../fixtures/application/manual-grant-outcome.json'),
  ),
  'fixtures/prior-authority/create.json': JSON.parse(
    open('../fixtures/prior-authority/create.json'),
  ),
  'fixtures/prior-authority/document-type.json': JSON.parse(
    open('../fixtures/prior-authority/document-type.json'),
  ),
  'fixtures/prior-authority/grant-decision.json': JSON.parse(
    open('../fixtures/prior-authority/grant-decision.json'),
  ),
  'fixtures/prior-authority/update.json': JSON.parse(
    open('../fixtures/prior-authority/update.json'),
  ),
  'fixtures/work-list/assign.json': JSON.parse(open('../fixtures/work-list/assign.json')),
});
const evidenceDocument = open('../fixtures/documents/k6-evidence.pdf', 'b');

export function createProviderApplicationRuntime(config, token, context) {
  return {
    loadFixture(path) {
      if (!fixtures[path]) {
        throw new Error(`Unknown fixture: ${path}`);
      }
      return fixtures[path];
    },
    applyContext(value, runContext) {
      return applyRunContext(value, runContext);
    },
    documentFixture(path) {
      if (path !== 'fixtures/documents/k6-evidence.pdf') {
        throw new Error(`Unknown document fixture: ${path}`);
      }
      return http.file(
        evidenceDocument,
        'k6-evidence.pdf',
        'application/pdf',
      );
    },
    request(method, path, body, params) {
      return request(config, token, method, path, body, {
        ...params,
        tags: {
          ...params?.tags,
          ...context.metricTags,
        },
      });
    },
    expectStatus(response, statuses) {
      return expectStatus(response, statuses, {
        method: response.request.method,
        path: response.request.url.replace(config.apiBaseUrl, ''),
        runId: context.runContext.runId,
        applicationId: context.applicationId || context.applicationDraftId,
      });
    },
    pollFor(description, predicate) {
      return pollFor(description, predicate, config);
    },
  };
}
