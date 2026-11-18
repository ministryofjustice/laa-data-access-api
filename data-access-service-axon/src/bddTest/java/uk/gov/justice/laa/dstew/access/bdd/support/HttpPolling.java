package uk.gov.justice.laa.dstew.access.bdd.support;

import java.time.Duration;
import java.util.function.Predicate;
import uk.gov.justice.laa.dstew.access.bdd.config.BddConfiguration;

public final class HttpPolling {
  private final ApiClient apiClient;
  private final Duration timeout;
  private final Duration interval;

  public HttpPolling(ApiClient apiClient, BddConfiguration configuration) {
    this.apiClient = apiClient;
    timeout = configuration.timeout();
    interval = configuration.pollInterval();
  }

  public ApiResponse awaitGet(String path, Predicate<ApiResponse> condition) {
    long deadline = System.nanoTime() + timeout.toNanos();
    ApiResponse latest = null;
    while (System.nanoTime() < deadline) {
      latest = apiClient.get(path);
      if (latest.status() == 200 && condition.test(latest)) return latest;
      pause(path);
    }
    throw new AssertionError("Timed out waiting for " + path + "; last response: " + latest);
  }

  private void pause(String path) {
    try {
      Thread.sleep(interval.toMillis());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while polling " + path, exception);
    }
  }
}
