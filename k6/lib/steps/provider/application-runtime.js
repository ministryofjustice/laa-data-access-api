import http from 'k6/http';

import { expectStatus, request } from '../../http.js';
import { pollFor } from '../../polling.js';
import { applyRunContext, composeApplicationContent } from '../../run-context.js';

const fixtureDirectory = '../../../fixtures/';

export function createProviderApplicationRuntime(config, token, context) {
  return {
    loadFixture(path) {
      return JSON.parse(open(`${fixtureDirectory}${path.replace('fixtures/', '')}`));
    },
    applyContext(value, runContext) {
      return applyRunContext(value, runContext);
    },
    composeApplicationContent(updates, runContext) {
      return composeApplicationContent(updates, runContext);
    },
    documentFixture(path) {
      return http.file(
        open(`${fixtureDirectory}${path.replace('fixtures/', '')}`, 'b'),
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
