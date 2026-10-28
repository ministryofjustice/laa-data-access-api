function identifier(value) {
  return String(value)
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'run';
}

function hash(value) {
  let result = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    result ^= value.charCodeAt(index);
    result = Math.imul(result, 16777619);
  }
  return (result >>> 0).toString(16).padStart(8, '0');
}

function uuid(namespace, value) {
  const source = `${namespace}:${value}`;
  const hex = `${hash(source)}${hash(`${source}:1`)}${hash(`${source}:2`)}${hash(`${source}:3`)}`;
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-8${hex.slice(17, 20)}-${hex.slice(20, 32)}`;
}

export function createRunId() {
  return `run-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export function createRunContext({
  runId = createRunId(),
  vu = typeof __VU === 'number' ? __VU : 0,
  iteration = typeof __ITER === 'number' ? __ITER : 0,
} = {}) {
  const prefix = identifier(`${runId}-vu${vu}-it${iteration}`);

  return {
    runId: identifier(runId),
    vu,
    iteration,
    prefix,
    applicationId: uuid('application', prefix),
    priorAuthorityId: uuid('prior-authority', prefix),
    clientId: `k6-${prefix}-client`,
    laaReference: `LAA-K6-${prefix.toUpperCase()}`,
  };
}

export function applyRunContext(value, context) {
  if (typeof value === 'string') {
    return value.replace(/\{\{(\w+)\}\}/g, (_, key) => {
      if (context[key] === undefined) {
        throw new Error(`Run context is missing template value: ${key}`);
      }
      return String(context[key]);
    });
  }
  if (Array.isArray(value)) {
    return value.map((item) => applyRunContext(item, context));
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, applyRunContext(item, context)]),
    );
  }
  return value;
}
