package uk.gov.justice.laa.dstew.access.query;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Wraps Axon's subscription query with a single projection-readiness wait.
 *
 * <p>The subscription is opened before the caller's action runs, so a fast tracking processor
 * cannot emit an update that is missed between dispatch and the wait. The subscription is always
 * closed — on success, timeout, action failure, and interruption.
 */
@Component
public class SubscriptionProjectionGateway {

  private final QueryGateway queryGateway;
  private final Duration timeout;

  /** Creates the gateway using the configured projection timeout. */
  public SubscriptionProjectionGateway(
      QueryGateway queryGateway, @Value("${application.projection.timeout:5s}") Duration timeout) {
    this.queryGateway = queryGateway;
    this.timeout = timeout;
  }

  /**
   * Opens a subscription for {@code query}, runs {@code action}, then waits for the projection to
   * become readable.
   *
   * @param query the subscription query to register.
   * @param projectionType the expected projection read-model type.
   * @param action the side-effecting work (typically a command dispatch) whose event the projection
   *     must reflect.
   * @return {@code true} when the projection is readable within the configured timeout; {@code
   *     false} when the timeout expires — the action has still committed its event and the caller
   *     should signal acceptance rather than failure.
   * @throws RuntimeException propagated from {@code action} or the query bus; the interrupt flag is
   *     restored when Reactor wraps an {@link InterruptedException}.
   */
  public <R> boolean awaitProjection(Object query, Class<R> projectionType, Runnable action) {
    CompletableFuture<R> firstResult =
        Mono.from(queryGateway.subscriptionQuery(query, projectionType)).toFuture();
    try {
      action.run();
      return doAwait(firstResult);
    } finally {
      firstResult.cancel(true);
    }
  }

  private <R> boolean doAwait(CompletableFuture<R> firstResult) {
    try {
      Boolean result =
          Mono.fromFuture(firstResult)
              .map(ignored -> true)
              .timeout(timeout, Mono.just(false))
              .block();
      return Boolean.TRUE.equals(result);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw e;
    }
  }
}
