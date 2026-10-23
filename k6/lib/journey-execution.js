export function executeSteps(stepNames, context, registry, runtimes, scenario) {
  const steps = new Map(registry.map((step) => [step.filename, step]));

  stepNames.forEach((filename) => {
    const step = steps.get(filename);
    if (!step) {
      throw new Error(`Scenario references an unregistered step file: ${filename}`);
    }
    const runtime = runtimes[step.actor] || runtimes.Provider;
    if (!runtime) {
      throw new Error(`Scenario does not provide a runtime for ${step.actor}`);
    }
    context.metricTags = { scenario, step: step.canonicalName };
    step.execute(context, runtime);
  });
}
