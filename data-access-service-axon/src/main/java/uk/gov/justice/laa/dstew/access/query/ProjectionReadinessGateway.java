package uk.gov.justice.laa.dstew.access.query;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Waits for a projection to reflect a command by polling a readiness query against the shared read
 * model.
 *
 * <p>Polling, rather than a subscription query, is deliberate: without Axon Server, subscription
 * updates are in-memory, so an update emitted by the instance that owns the processor segment never
 * reaches a request waiting on another instance. Each wait is recorded on the {@code
 * projection.await} timer, tagged with the query and whether it ended {@code ready} or {@code
 * timeout}.
 */
@Slf4j
@Component
public class ProjectionReadinessGateway {

  static final String TIMER_NAME = "projection.await";
  private static final Duration INITIAL_POLL_INTERVAL = Duration.ofMillis(25);
  private static final Duration MAX_POLL_INTERVAL = Duration.ofMillis(250);
  private static final Executor VIRTUAL_THREADS = task -> Thread.ofVirtual().start(task);

  private final QueryGateway queryGateway;
  private final MeterRegistry meterRegistry;
  private final Duration timeout;

  /** Creates the gateway using the configured projection timeout. */
  public ProjectionReadinessGateway(
      QueryGateway queryGateway,
      MeterRegistry meterRegistry,
      @Value("${application.projection.timeout:5s}") Duration timeout) {
    this.queryGateway = queryGateway;
    this.meterRegistry = meterRegistry;
    this.timeout = timeout;
  }

  /**
   * Runs {@code action}, then polls the Boolean {@code readinessQuery} until it returns {@code
   * true}.
   *
   * <p>The readiness query must answer exactly whether the projection reflects the action's event,
   * using a database {@code exists} check rather than loading entities. Entity loads can return
   * stale state from the request's persistence context, and "readable" is not the same as "reflects
   * this command" (for example, a draft row is readable before its submission is projected).
   *
   * @param readinessQuery the Boolean query whose {@code true} result shows the projection reflects
   *     the action.
   * @param action the side-effecting work (typically a command dispatch) whose event the projection
   *     must reflect.
   * @return {@code true} when the projection is ready within the configured timeout; {@code false}
   *     when the timeout expires (each poll is bounded by the remaining time, so a slow query
   *     cannot hold the request past the deadline) — the action has still committed its event and
   *     the caller should signal acceptance rather than failure.
   * @throws RuntimeException propagated from {@code action}, or when the wait is interrupted (the
   *     interrupt flag is restored).
   */
  public boolean awaitProjection(Object readinessQuery, Runnable action) {
    action.run();
    long startNanos = System.nanoTime();
    long deadlineNanos = startNanos + timeout.toNanos();
    long pollIntervalNanos = INITIAL_POLL_INTERVAL.toNanos();
    while (true) {
      long remainingNanos = deadlineNanos - System.nanoTime();
      if (remainingNanos <= 0) {
        record(readinessQuery, "timeout", startNanos);
        return false;
      }
      if (pollWithin(readinessQuery, remainingNanos)) {
        record(readinessQuery, "ready", startNanos);
        return true;
      }
      sleep(Math.min(deadlineNanos - System.nanoTime(), pollIntervalNanos));
      pollIntervalNanos = Math.min(pollIntervalNanos * 2, MAX_POLL_INTERVAL.toNanos());
    }
  }

  /**
   * Runs one readiness poll, bounded by the time left before the deadline. The poll is dispatched
   * on a virtual thread so the bound holds even when the query bus handles the query on the calling
   * thread. A poll that fails or overruns counts as "not yet ready": the command has committed, so
   * the caller should fall back to {@code 202 Accepted} rather than fail the request.
   */
  private boolean pollWithin(Object readinessQuery, long remainingNanos) {
    CompletableFuture<Boolean> poll =
        CompletableFuture.supplyAsync(
                () -> queryGateway.query(readinessQuery, Boolean.class), VIRTUAL_THREADS)
            .thenCompose(Function.identity());
    try {
      return Boolean.TRUE.equals(poll.get(remainingNanos, TimeUnit.NANOSECONDS));
    } catch (TimeoutException e) {
      poll.cancel(true);
      log.warn("Projection readiness poll did not complete before the deadline");
      return false;
    } catch (ExecutionException e) {
      log.warn("Projection readiness poll failed; continuing to wait", e.getCause());
      return false;
    } catch (InterruptedException e) {
      poll.cancel(true);
      throw interrupted(e);
    }
  }

  private void sleep(long nanos) {
    if (nanos <= 0) {
      return;
    }
    try {
      Thread.sleep(Duration.ofNanos(nanos));
    } catch (InterruptedException e) {
      throw interrupted(e);
    }
  }

  private static IllegalStateException interrupted(InterruptedException e) {
    Thread.currentThread().interrupt();
    return new IllegalStateException("Interrupted while awaiting projection", e);
  }

  private void record(Object query, String outcome, long startNanos) {
    Timer.builder(TIMER_NAME)
        .description("Time spent waiting for a projection to reflect a command")
        .tag("query", query.getClass().getSimpleName())
        .tag("outcome", outcome)
        .register(meterRegistry)
        .record(Duration.ofNanos(System.nanoTime() - startNanos));
  }
}
