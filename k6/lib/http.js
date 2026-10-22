import http from 'k6/http';

export function request(config, token, method, path, body, params = {}) {
  const response = http.request(method, `${config.apiBaseUrl}${path}`, body, {
    ...params,
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Service-Name': config.serviceName,
      ...params.headers,
    },
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
        `priorAuthority=${diagnostics.priorAuthorityId || 'absent'} correlationId=${correlationId}`,
    );
  }
  return response;
}
