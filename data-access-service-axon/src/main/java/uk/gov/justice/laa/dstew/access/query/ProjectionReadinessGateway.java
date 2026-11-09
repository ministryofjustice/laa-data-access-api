package uk.gov.justice.laa.dstew.access.query;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.function.BooleanSupplier;
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
   *     when the timeout expires — the action has still committed its event and the caller should
   *     signal acceptance rather than failure.
   * @throws RuntimeException propagated from {@code action}, or when the wait is interrupted (the
   *     interrupt flag is restored).
   */
  public boolean awaitProjection(Object readinessQuery, Runnable action) {
    action.run();
    return await(
        readinessQuery,
        () -> Boolean.TRUE.equals(queryGateway.query(readinessQuery, Boolean.class).join()));
  }

  private boolean await(Object query, BooleanSupplier readable) {
    long startNanos = System.nanoTime();
    long deadlineNanos = startNanos + timeout.toNanos();
    long pollIntervalNanos = INITIAL_POLL_INTERVAL.toNanos();
    while (!pollQuietly(readable)) {
      long remainingNanos = deadlineNanos - System.nanoTime();
      if (remainingNanos <= 0) {
        record(query, "timeout", startNanos);
        return false;
      }
      sleep(Math.min(remainingNanos, pollIntervalNanos));
      pollIntervalNanos = Math.min(pollIntervalNanos * 2, MAX_POLL_INTERVAL.toNanos());
    }
    record(query, "ready", startNanos);
    return true;
  }

  /**
   * Treats a failed poll as "not yet readable": the command has committed, so a transient read
   * failure should fall back to {@code 202 Accepted} rather than fail the request.
   */
  private boolean pollQuietly(BooleanSupplier readable) {
    try {
      return readable.getAsBoolean();
    } catch (RuntimeException e) {
      log.warn("Projection readiness poll failed; continuing to wait", e);
      return false;
    }
  }

  private void sleep(long nanos) {
    try {
      Thread.sleep(Duration.ofNanos(nanos));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while awaiting projection", e);
    }
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
