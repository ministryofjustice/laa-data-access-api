const DEFAULTS = {
  apiBaseUrl: 'http://localhost:8080',
  oauthTokenUrl: 'http://localhost:9999/entra/token',
  serviceName: 'CIVIL_APPLY',
  pollTimeoutMs: 30000,
  pollIntervalMs: 500,
  summaryPath: 'k6/results/summary.json',
  contentionVus: 2,
  performanceVus: 5,
  performanceDuration: '5m',
};

const ACTOR_ENVIRONMENT = {
  provider: ['K6_PROVIDER_CLIENT_ID', 'K6_PROVIDER_CLIENT_SECRET'],
  applicationCaseworker: [
    'K6_APPLICATION_CASEWORKER_CLIENT_ID',
    'K6_APPLICATION_CASEWORKER_CLIENT_SECRET',
  ],
  paCaseworker: ['K6_PA_CASEWORKER_CLIENT_ID', 'K6_PA_CASEWORKER_CLIENT_SECRET'],
  contender: ['K6_CONTENDER_CLIENT_ID', 'K6_CONTENDER_CLIENT_SECRET'],
};

function requiredUrl(name, value) {
  if (!/^https?:\/\/[^\s/]+(?:\/[^\s]*)?$/i.test(value)) {
    throw new Error(`${name} must be an absolute HTTP(S) URL`);
  }
  return value.replace(/\/$/, '');
}

function positiveInteger(name, value) {
  if (!/^[1-9]\d*$/.test(value)) {
    throw new Error(`${name} must be a positive integer`);
  }
  return Number(value);
}

function requiredValue(name, value) {
  if (!value || !value.trim()) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function relativePath(name, value) {
  const path = requiredValue(name, value);
  if (path.startsWith('/') || path.split('/').includes('..')) {
    throw new Error(`${name} must be a relative path inside the repository`);
  }
  return path;
}

function duration(name, value) {
  const valueToValidate = requiredValue(name, value);
  if (!/^\d+(?:\.\d+)?(?:ms|s|m|h)$/.test(valueToValidate)) {
    throw new Error(`${name} must be a k6 duration such as 5m`);
  }
  return valueToValidate;
}

function parseThresholds(value) {
  if (!value) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(value);
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      throw new Error();
    }
    return parsed;
  } catch (_) {
    throw new Error('K6_THRESHOLDS must be a JSON object');
  }
}

function actorCredentials(environment, actor) {
  const [clientIdName, clientSecretName] = ACTOR_ENVIRONMENT[actor];
  const clientId = environment[clientIdName];
  const clientSecret = environment[clientSecretName];

  if (!clientId && !clientSecret) {
    return null;
  }

  return {
    clientId: requiredValue(clientIdName, clientId),
    clientSecret: requiredValue(clientSecretName, clientSecret),
  };
}

export function loadConfig(environment = __ENV, requiredActors = []) {
  const actors = {};
  Object.keys(ACTOR_ENVIRONMENT).forEach((actor) => {
    const credentials = actorCredentials(environment, actor);
    if (credentials) {
      actors[actor] = credentials;
    }
  });

  if (!actors.paCaseworker && actors.applicationCaseworker) {
    actors.paCaseworker = actors.applicationCaseworker;
  }

  requiredActors.forEach((actor) => {
    if (!actors[actor]) {
      const [clientIdName, clientSecretName] = ACTOR_ENVIRONMENT[actor] || [];
      throw new Error(
        `Configuration for ${actor} is required (${clientIdName} and ${clientSecretName})`,
      );
    }
  });

  return {
    apiBaseUrl: requiredUrl('K6_API_BASE_URL', environment.K6_API_BASE_URL || DEFAULTS.apiBaseUrl),
    oauthTokenUrl: requiredUrl(
      'K6_OAUTH_TOKEN_URL',
      environment.K6_OAUTH_TOKEN_URL || DEFAULTS.oauthTokenUrl,
    ),
    serviceName: requiredValue(
      'K6_SERVICE_NAME',
      environment.K6_SERVICE_NAME || DEFAULTS.serviceName,
    ),
    pollTimeoutMs: positiveInteger(
      'K6_POLL_TIMEOUT_MS',
      environment.K6_POLL_TIMEOUT_MS || String(DEFAULTS.pollTimeoutMs),
    ),
    pollIntervalMs: positiveInteger(
      'K6_POLL_INTERVAL_MS',
      environment.K6_POLL_INTERVAL_MS || String(DEFAULTS.pollIntervalMs),
    ),
    summaryPath: relativePath(
      'K6_SUMMARY_PATH',
      environment.K6_SUMMARY_PATH || DEFAULTS.summaryPath,
    ),
    contentionVus: positiveInteger(
      'K6_CONTENTION_VUS',
      environment.K6_CONTENTION_VUS || String(DEFAULTS.contentionVus),
    ),
    performanceVus: positiveInteger(
      'K6_PERFORMANCE_VUS',
      environment.K6_PERFORMANCE_VUS || String(DEFAULTS.performanceVus),
    ),
    performanceDuration: duration(
      'K6_PERFORMANCE_DURATION',
      environment.K6_PERFORMANCE_DURATION || DEFAULTS.performanceDuration,
    ),
    thresholds: parseThresholds(environment.K6_THRESHOLDS),
    actors,
  };
}
