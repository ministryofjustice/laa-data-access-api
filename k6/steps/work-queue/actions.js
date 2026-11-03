import { assignWorkListItem, findWorkListItem, requireRuntime, verifyWorkListItemRemoved } from '../step-support.js';

export const find = (options) => (context, runtime) => findWorkListItem(context, runtime, options);
export const assign = (options) => (context, runtime) => assignWorkListItem(context, runtime, options);
export const verifyRemoved = (options, outputKey) => (context, runtime) => {
  const dependencies = requireRuntime(runtime);
  verifyWorkListItemRemoved(context, dependencies, options);
  context[outputKey] = true;
};
export const grant = ({ path, fixture, contextKey, outputKey, expectedStatus }) => (context, runtime) => {
  const dependencies = requireRuntime(runtime);
  const decision = dependencies.applyContext(dependencies.loadFixture(fixture), context.runContext);
  dependencies.expectStatus(dependencies.request('PATCH', path(context), decision, {
    headers: { 'Content-Type': 'application/json' },
  }), expectedStatus);
  context[outputKey] = context[contextKey];
};
