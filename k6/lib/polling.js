import { sleep } from 'k6';

export function pollFor(description, predicate, config) {
  const deadline = Date.now() + config.pollTimeoutMs;
  let lastResult;

  while (Date.now() <= deadline) {
    lastResult = predicate();
    if (lastResult.matched) {
      return lastResult.value;
    }
    if (!lastResult.retry) {
      throw new Error(
        `Unexpected result while waiting for ${description}: ${lastResult.reason || 'not retryable'}`,
      );
    }
    sleep(config.pollIntervalMs / 1000);
  }

  throw new Error(
    `Timed out waiting for ${description} after ${config.pollTimeoutMs}ms`,
  );
}
