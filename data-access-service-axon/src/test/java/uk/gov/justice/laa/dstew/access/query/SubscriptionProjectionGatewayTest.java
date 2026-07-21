package uk.gov.justice.laa.dstew.access.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.messaging.queryhandling.SubscriptionQueryResult;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;

class SubscriptionProjectionGatewayTest {

  private QueryGateway queryGateway;
  private SubscriptionProjectionGateway gateway;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    queryGateway = mock(QueryGateway.class);
    gateway = new SubscriptionProjectionGateway(queryGateway, Duration.ofMillis(100));
  }

  // ── awaitProjection: initial-result paths ────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void givenPresentInitialResult_whenAwaitProjection_thenReturnsTrueImmediately() {
    ApplicationReadModel readModel = mock(ApplicationReadModel.class);
    SubscriptionQueryResult<Optional<ApplicationReadModel>, ApplicationReadModel> subscription =
        subscription(Mono.just(Optional.of(readModel)), Flux.never());

    boolean result = gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {});

    assertThat(result).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenEmptyInitialResultAndFirstUpdate_whenAwaitProjection_thenReturnsTrue() {
    ApplicationReadModel readModel = mock(ApplicationReadModel.class);
    SubscriptionQueryResult<Optional<ApplicationReadModel>, ApplicationReadModel> subscription =
        subscription(Mono.just(Optional.empty()), Flux.just(readModel));

    boolean result = gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {});

    assertThat(result).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenNeverCompletingInitialResult_whenAwaitProjection_thenReturnsFalseWithinTimeout() {
    subscription(Mono.never(), Flux.never());

    long startNs = System.nanoTime();
    boolean result = gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {});
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

    assertThat(result).isFalse();
    assertThat(elapsedMs).isLessThan(2_000L);
  }

  // ── awaitProjection: error and interrupt paths ───────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void givenInitialResultError_whenAwaitProjection_thenPropagatesError() {
    subscription(Mono.error(new RuntimeException("query bus failure")), Flux.never());

    assertThatThrownBy(() -> gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {}))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("query bus failure");
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenInterruptedBlock_whenAwaitProjection_thenRestoresInterruptFlagAndRethrows() {
    RuntimeException wrappedInterrupt =
        new RuntimeException(new InterruptedException("interrupted"));
    subscription(Mono.error(wrappedInterrupt), Flux.never());

    try {
      assertThatThrownBy(
              () -> gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {}))
          .isInstanceOf(RuntimeException.class)
          .hasCauseInstanceOf(InterruptedException.class);

      assertThat(Thread.currentThread().isInterrupted())
          .as("interrupt flag must be restored after Reactor wraps InterruptedException")
          .isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenNonInterruptRuntimeException_whenAwaitProjection_thenDoesNotSetInterruptFlag() {
    subscription(Mono.error(new RuntimeException("other failure")), Flux.never());

    assertThatThrownBy(() -> gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {}))
        .isInstanceOf(RuntimeException.class);

    assertThat(Thread.currentThread().isInterrupted()).isFalse();
  }

  // ── awaitProjection: action lifecycle ────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void givenActionThrows_whenAwaitProjection_thenPropagatesAndClosesSubscription() {
    SubscriptionQueryResult<Optional<ApplicationReadModel>, ApplicationReadModel> subscription =
        subscription(Mono.just(Optional.empty()), Flux.never());

    assertThatThrownBy(
            () ->
                gateway.awaitProjection(
                    query(),
                    ApplicationReadModel.class,
                    () -> {
                      throw new RuntimeException("dispatch failed");
                    }))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("dispatch failed");

    verify(subscription).close();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenSuccessfulAction_whenAwaitProjection_thenSubscriptionIsClosed() {
    ApplicationReadModel readModel = mock(ApplicationReadModel.class);
    SubscriptionQueryResult<Optional<ApplicationReadModel>, ApplicationReadModel> subscription =
        subscription(Mono.just(Optional.of(readModel)), Flux.never());

    gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {});

    verify(subscription).close();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private FindApplicationByIdQuery query() {
    return new FindApplicationByIdQuery(UUID.randomUUID());
  }

  @SuppressWarnings("unchecked")
  private SubscriptionQueryResult<Optional<ApplicationReadModel>, ApplicationReadModel>
      subscription(
          Mono<Optional<ApplicationReadModel>> initialResult, Flux<ApplicationReadModel> updates) {
    SubscriptionQueryResult<Optional<ApplicationReadModel>, ApplicationReadModel> subscription =
        mock(SubscriptionQueryResult.class);
    when(subscription.initialResult()).thenReturn(initialResult);
    when(subscription.updates()).thenReturn(updates);
    when(queryGateway.subscriptionQuery(any(), any(ResponseType.class), any(ResponseType.class)))
        .thenReturn(subscription);
    return subscription;
  }
}
