export function composeDraft(runtime, runContext) {
  const create = runtime.applyContext(
    runtime.loadFixture('fixtures/application/create.json'),
    runContext,
  );
  return create;
}
