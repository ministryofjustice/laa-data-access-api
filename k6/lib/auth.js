import http from 'k6/http';

export function fetchToken({
  tokenUrl,
  clientId,
  clientSecret,
  scope = 'api://laa-data-access-api/.default',
  actor,
}) {
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
    throw new Error(`OAuth token request failed for ${actor}: HTTP ${res.status}`);
  }

  const body = res.json();
  if (!body || !body.access_token) {
    throw new Error(`OAuth token response was invalid for ${actor}: access_token is missing`);
  }

  return body.access_token;
}

export function fetchTokens(config, actors) {
  return actors.reduce((tokens, actor) => {
    const credentials = config.actors[actor];
    tokens[actor] = fetchToken({
      tokenUrl: config.oauthTokenUrl,
      clientId: credentials.clientId,
      clientSecret: credentials.clientSecret,
      actor,
    });
    return tokens;
  }, {});
}
