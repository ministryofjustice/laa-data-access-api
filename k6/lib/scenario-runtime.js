import { createRunContext } from './run-context.js';
import { executeSteps } from './journey-execution.js';
import { stepRegistry } from './step-registry.js';
import { createProviderApplicationRuntime } from './steps/provider/application-runtime.js';

export function createScenarioContext() {
  return { runContext: createRunContext() };
}

export function createScenarioRuntimes(config, tokens, context, overrides = {}) {
  const runtime = (token) => createProviderApplicationRuntime(config, token, context);
  return {
    Provider: runtime(tokens.provider),
    'Provider/system': runtime(tokens.provider),
    'Application Caseworker': runtime(tokens.applicationCaseworker),
    'PA Caseworker': runtime(tokens.paCaseworker),
    Contender: runtime(tokens.contender),
    ...overrides,
  };
}

export function runScenarioSteps(stepNames, config, tokens, context, scenario, overrides) {
  executeSteps(
    stepNames,
    context,
    stepRegistry,
    createScenarioRuntimes(config, tokens, context, overrides),
    scenario,
  );
  return context;
}
