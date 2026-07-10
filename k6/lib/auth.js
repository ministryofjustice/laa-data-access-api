import http from 'k6/http';

/**
 * Fetches an OAuth2 client_credentials token from the mock-oauth2-server (or any compatible
 * OAuth2 token endpoint).
 *
 * Intended to be called once in the k6 `setup()` function and passed to VUs via the data object.
 *
 * @param {string} oauthBaseUrl  Base URL of the OAuth server, e.g. "http://localhost:9999".
 *                               The token endpoint is assumed to be at `{oauthBaseUrl}/entra/token`.
 * @param {string} [clientId]    OAuth client_id (default: "test")
 * @param {string} [clientSecret] OAuth client_secret (default: "test")
 * @param {string} [scope]       OAuth scope (default: "api://laa-data-access-api/.default")
 * @returns {string} Raw JWT access token string
 * @throws if the token endpoint returns a non-200 or the response has no access_token
 */
export function fetchToken(
  oauthBaseUrl,
  clientId = 'test',
  clientSecret = 'test',
  scope = 'api://laa-data-access-api/.default',
) {
  const tokenUrl = `${oauthBaseUrl}/entra/token`;

  const res = http.post(
    tokenUrl,
    {
      grant_type: 'client_credentials',
      client_id: clientId,
      client_secret: clientSecret,
      scope,
    },
    {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      tags: { name: 'POST /entra/token (setup)' },
    },
  );

  if (res.status !== 200) {
    throw new Error(
      `Failed to fetch OAuth token from ${tokenUrl}: HTTP ${res.status}\n${res.body}`,
    );
  }

  const body = res.json();
  if (!body || !body.access_token) {
    throw new Error(
      `Token response from ${tokenUrl} did not contain access_token. Body: ${res.body}`,
    );
  }

  return body.access_token;
}
