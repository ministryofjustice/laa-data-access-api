package uk.gov.justice.laa.dstew.access.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationSubmissionProjectedQuery;

@ExtendWith(MockitoExtension.class)
class ProjectionReadinessGatewayTest {

  private static final Duration SHORT_TIMEOUT = Duration.ofMillis(100);
  private static final Duration PATIENT_TIMEOUT = Duration.ofSeconds(2);
  private static final String QUERY_NAME = "ApplicationSubmissionProjectedQuery";

  @Mock private QueryGateway queryGateway;
  @Mock private Runnable action;

  private SimpleMeterRegistry meterRegistry;
  private ApplicationSubmissionProjectedQuery query;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    query = new ApplicationSubmissionProjectedQuery(UUID.randomUUID());
  }

  @Test
  void givenProjectionReady_whenAwaitProjection_thenRunsActionBeforePollingAndReturnsTrue() {
    when(queryGateway.query(query, Boolean.class)).thenReturn(completed(true));

    boolean result = gateway(SHORT_TIMEOUT).awaitProjection(query, action);

    assertThat(result).isTrue();
    InOrder order = inOrder(action, queryGateway);
    order.verify(action).run();
    order.verify(queryGateway).query(query, Boolean.class);
    assertThat(timerCount("ready")).isEqualTo(1);
  }

  @Test
  void givenProjectionUpdatedOnAnotherInstance_whenAwaitProjection_thenReturnsTrueOnLaterPoll() {
    when(queryGateway.query(query, Boolean.class))
        .thenReturn(completed(false))
        .thenReturn(completed(false))
        .thenReturn(completed(true));

    boolean result = gateway(PATIENT_TIMEOUT).awaitProjection(query, action);

    assertThat(result).isTrue();
    verify(queryGateway, times(3)).query(query, Boolean.class);
    assertThat(timerCount("ready")).isEqualTo(1);
  }

  @Test
  void givenProjectionNeverReady_whenAwaitProjection_thenReturnsFalseAndRecordsTimeout() {
    when(queryGateway.query(query, Boolean.class)).thenReturn(completed(false));

    long startNs = System.nanoTime();
    boolean result = gateway(SHORT_TIMEOUT).awaitProjection(query, action);
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

    assertThat(result).isFalse();
    assertThat(elapsedMs).isBetween(SHORT_TIMEOUT.toMillis(), 2_000L);
    assertThat(timerCount("timeout")).isEqualTo(1);
    assertThat(timerCount("ready")).isZero();
  }

  @Test
  void givenNullReadinessResult_whenAwaitProjection_thenTreatsAsNotReady() {
    when(queryGateway.query(query, Boolean.class)).thenReturn(completed(null));

    boolean result = gateway(SHORT_TIMEOUT).awaitProjection(query, action);

    assertThat(result).isFalse();
  }

  @Test
  void givenPollQueryFails_whenAwaitProjection_thenKeepsPollingUntilReady() {
    when(queryGateway.query(query, Boolean.class))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("read failure")))
        .thenReturn(completed(true));

    boolean result = gateway(PATIENT_TIMEOUT).awaitProjection(query, action);

    assertThat(result).isTrue();
  }

  @Test
  void givenActionThrows_whenAwaitProjection_thenPropagatesWithoutPolling() {
    doThrow(new RuntimeException("dispatch failed")).when(action).run();

    assertThatThrownBy(() -> gateway(SHORT_TIMEOUT).awaitProjection(query, action))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("dispatch failed");

    verifyNoInteractions(queryGateway);
    assertThat(meterRegistry.find(ProjectionReadinessGateway.TIMER_NAME).timers()).isEmpty();
  }

  @Test
  void givenThreadInterruptedWhileWaiting_whenAwaitProjection_thenRestoresInterruptFlag() {
    when(queryGateway.query(query, Boolean.class)).thenReturn(completed(false));

    try {
      assertThatThrownBy(
              () ->
                  gateway(PATIENT_TIMEOUT)
                      .awaitProjection(query, () -> Thread.currentThread().interrupt()))
          .isInstanceOf(IllegalStateException.class)
          .hasCauseInstanceOf(InterruptedException.class);

      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  private ProjectionReadinessGateway gateway(Duration timeout) {
    return new ProjectionReadinessGateway(queryGateway, meterRegistry, timeout);
  }

  private static CompletableFuture<Boolean> completed(Boolean value) {
    return CompletableFuture.completedFuture(value);
  }

  private long timerCount(String outcome) {
    var timer =
        meterRegistry
            .find(ProjectionReadinessGateway.TIMER_NAME)
            .tag("query", QUERY_NAME)
            .tag("outcome", outcome)
            .timer();
    return timer == null ? 0 : timer.count();
  }
}
