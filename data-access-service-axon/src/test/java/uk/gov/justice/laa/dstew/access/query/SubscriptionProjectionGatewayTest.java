package uk.gov.justice.laa.dstew.access.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;

class SubscriptionProjectionGatewayTest {

  private QueryGateway queryGateway;
  private SubscriptionProjectionGateway gateway;

  @BeforeEach
  void setUp() {
    queryGateway = mock(QueryGateway.class);
    gateway = new SubscriptionProjectionGateway(queryGateway, Duration.ofMillis(100));
  }

  // ── awaitProjection: initial-result paths ────────────────────────────────

  @Test
  void givenPresentInitialResult_whenAwaitProjection_thenReturnsTrueImmediately() {
    ApplicationReadModel readModel = mock(ApplicationReadModel.class);
    subscription(Mono.just(readModel));

    boolean result = gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {});

    assertThat(result).isTrue();
  }

  @Test
  void givenEmptyInitialResultAndFirstUpdate_whenAwaitProjection_thenReturnsTrue() {
    ApplicationReadModel readModel = mock(ApplicationReadModel.class);
    Sinks.One<ApplicationReadModel> update = Sinks.one();
    subscription(update.asMono());

    boolean result =
        gateway.awaitProjection(
            query(), ApplicationReadModel.class, () -> update.tryEmitValue(readModel));

    assertThat(result).isTrue();
  }

  @Test
  void givenNeverCompletingInitialResult_whenAwaitProjection_thenReturnsFalseWithinTimeout() {
    subscription(Mono.never());

    long startNs = System.nanoTime();
    boolean result = gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {});
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

    assertThat(result).isFalse();
    assertThat(elapsedMs).isLessThan(2_000L);
  }

  @Test
  void givenFalseInitialBooleanAndTrueUpdate_whenAwaitProjection_thenReturnsTrue() {
    Sinks.One<Boolean> update = Sinks.one();
    booleanSubscription(Flux.concat(Mono.just(false), update.asMono()));

    boolean result = gateway.awaitProjection(query(), () -> update.tryEmitValue(Boolean.TRUE));

    assertThat(result).isTrue();
  }

  @Test
  void givenFalseBooleanWithoutUpdate_whenAwaitProjection_thenReturnsFalseWithinTimeout() {
    booleanSubscription(Flux.just(false).concatWith(Flux.never()));

    long startNs = System.nanoTime();
    boolean result = gateway.awaitProjection(query(), () -> {});
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

    assertThat(result).isFalse();
    assertThat(elapsedMs).isLessThan(2_000L);
  }

  // ── awaitProjection: error and interrupt paths ───────────────────────────

  @Test
  void givenInitialResultError_whenAwaitProjection_thenPropagatesError() {
    subscription(Mono.error(new RuntimeException("query bus failure")));

    assertThatThrownBy(() -> gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {}))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("query bus failure");
  }

  @Test
  void givenInterruptedBlock_whenAwaitProjection_thenRestoresInterruptFlagAndRethrows() {
    RuntimeException wrappedInterrupt =
        new RuntimeException(new InterruptedException("interrupted"));
    subscription(Mono.error(wrappedInterrupt));

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
  void givenNonInterruptRuntimeException_whenAwaitProjection_thenDoesNotSetInterruptFlag() {
    subscription(Mono.error(new RuntimeException("other failure")));

    assertThatThrownBy(() -> gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {}))
        .isInstanceOf(RuntimeException.class);

    assertThat(Thread.currentThread().isInterrupted()).isFalse();
  }

  // ── awaitProjection: action lifecycle ────────────────────────────────────

  @Test
  void givenActionThrows_whenAwaitProjection_thenPropagatesAndClosesSubscription() {
    AtomicBoolean cancelled = new AtomicBoolean();
    subscription(Flux.<ApplicationReadModel>never().doOnCancel(() -> cancelled.set(true)));

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

    assertThat(cancelled).isTrue();
  }

  @Test
  void givenSuccessfulAction_whenAwaitProjection_thenSubscriptionIsClosed() {
    ApplicationReadModel readModel = mock(ApplicationReadModel.class);
    AtomicBoolean cancelled = new AtomicBoolean();
    subscription(
        Flux.just(readModel).concatWith(Flux.never()).doOnCancel(() -> cancelled.set(true)));

    gateway.awaitProjection(query(), ApplicationReadModel.class, () -> {});

    assertThat(cancelled).isTrue();
  }

  @Test
  void givenDelayedResult_whenFindProjection_thenReturnsFirstAvailableProjection() {
    ApplicationReadModel notification = mock(ApplicationReadModel.class);
    ApplicationReadModel hydrated = mock(ApplicationReadModel.class);
    FindApplicationByIdQuery query = query();
    subscription(Mono.delay(Duration.ofMillis(10)).map(ignored -> notification));
    when(queryGateway.query(query, ApplicationReadModel.class))
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(hydrated));

    Optional<ApplicationReadModel> result =
        gateway.findProjection(query, ApplicationReadModel.class);

    assertThat(result).contains(hydrated);
  }

  @Test
  void givenHydrationQueryReturnsNull_whenFindProjection_thenReturnsEmpty() {
    ApplicationReadModel notification = mock(ApplicationReadModel.class);
    FindApplicationByIdQuery query = query();
    subscription(Mono.just(notification));
    when(queryGateway.query(query, ApplicationReadModel.class))
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

    Optional<ApplicationReadModel> result =
        gateway.findProjection(query, ApplicationReadModel.class);

    assertThat(result).isEmpty();
  }

  @Test
  void givenNoResultBeforeTimeout_whenFindProjection_thenReturnsEmpty() {
    subscription(Mono.never());

    Optional<ApplicationReadModel> result =
        gateway.findProjection(query(), ApplicationReadModel.class);

    assertThat(result).isEmpty();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private FindApplicationByIdQuery query() {
    return new FindApplicationByIdQuery(UUID.randomUUID());
  }

  private void subscription(org.reactivestreams.Publisher<ApplicationReadModel> results) {
    when(queryGateway.subscriptionQuery(any(), eq(ApplicationReadModel.class))).thenReturn(results);
  }

  private void booleanSubscription(org.reactivestreams.Publisher<Boolean> results) {
    when(queryGateway.subscriptionQuery(any(), eq(Boolean.class))).thenReturn(results);
  }
}
