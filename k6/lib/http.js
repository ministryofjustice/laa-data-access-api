import http from 'k6/http';

function cleanDiagnostic(value) {
  return String(value).replace(/\s+/g, ' ').trim().slice(0, 500);
}

function problemSummary(response) {
  const contentType = response.headers['Content-Type'] || response.headers['content-type'] || '';
  if (!contentType.includes('json')) {
    return 'problem=unavailable';
  }

  const problem = response.json();
  if (!problem || typeof problem !== 'object') {
    return 'problem=unavailable';
  }

  const fields = [
    ['title', problem.title],
    ['detail', problem.detail],
    ['errors', Array.isArray(problem.errors) ? problem.errors.slice(0, 5).join('; ') : undefined],
  ].filter(([, value]) => value);
  return fields.length === 0
    ? 'problem=unavailable'
    : fields.map(([name, value]) => `${name}=${cleanDiagnostic(value)}`).join(' ');
}

function responsePreview(response) {
  if (__ENV.K6_DEBUG_RESPONSE_BODY !== 'true' || !response.body) {
    return '';
  }
  return ` responsePreview=${cleanDiagnostic(response.body)}`;
}

export function request(config, token, method, path, body, params = {}) {
  const headers = {
    Authorization: `Bearer ${token}`,
    'X-Service-Name': config.serviceName,
    ...params.headers,
  };
  const contentType = headers['Content-Type'] || headers['content-type'] || '';
  const requestBody =
    contentType.includes('application/json') && body && typeof body === 'object'
      ? JSON.stringify(body)
      : body;
  const response = http.request(method, `${config.apiBaseUrl}${path}`, requestBody, {
    ...params,
    headers,
  });
  return response;
}

export function expectStatus(response, expectedStatuses, diagnostics) {
  const expected = Array.isArray(expectedStatuses) ? expectedStatuses : [expectedStatuses];
  if (!expected.includes(response.status)) {
    const correlationId =
      response.headers['X-Correlation-ID'] || response.headers['x-correlation-id'] || 'absent';
    throw new Error(
      `${diagnostics.method} ${diagnostics.path} returned HTTP ${response.status}; ` +
        `run=${diagnostics.runId || 'absent'} application=${diagnostics.applicationId || 'absent'} ` +
        `priorAuthority=${diagnostics.priorAuthorityId || 'absent'} correlationId=${correlationId} ` +
        `${problemSummary(response)}${responsePreview(response)}`,
    );
  }
  return response;
}
