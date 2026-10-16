package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.junit.jupiter.api.Test;

class AggregateEventHistoryReaderTest {

  private final EventStore eventStore = mock(EventStore.class);
  private final EventStoreTransaction transaction = mock(EventStoreTransaction.class);
  private final ProcessingContext context = mock(ProcessingContext.class);
  private final AggregateEventHistoryReader reader = new AggregateEventHistoryReader(eventStore);

  @Test
  void includesFirstEventAndWaitsForDelayedBatchInOrder() {
    EventMessage first = mock(EventMessage.class);
    EventMessage second = mock(EventMessage.class);
    CompletableFuture<EventMessage> delayed = new CompletableFuture<>();
    MessageStream<EventMessage> stream =
        spy(
            MessageStream.just(
                    first,
                    message ->
                        Context.with(LegacyResources.AGGREGATE_TYPE_KEY, "ApplicationAggregate"))
                .concatWith(
                    MessageStream.fromFuture(
                        delayed,
                        message ->
                            Context.with(
                                LegacyResources.AGGREGATE_TYPE_KEY, "ApplicationAggregate"))));
    when(eventStore.transaction(context)).thenReturn(transaction);
    doReturn(stream).when(transaction).source(any(SourcingCondition.class));

    var result =
        CompletableFuture.supplyAsync(
            () -> reader.read("ApplicationAggregate", UUID.randomUUID(), context));
    assertThat(result).isNotDone();
    delayed.complete(second);

    assertThat(result.join()).containsExactly(first, second);
    verify(stream, atLeastOnce()).close();
  }

  @Test
  void excludesOtherAggregateTypesEvenWhenStorageReturnsTheSameIdentifier() {
    MessageStream<EventMessage> stream =
        MessageStream.just(
            mock(EventMessage.class),
            message -> Context.with(LegacyResources.AGGREGATE_TYPE_KEY, "ApplicationAggregate"));
    when(eventStore.transaction(context)).thenReturn(transaction);
    doReturn(stream).when(transaction).source(any(SourcingCondition.class));

    assertThat(reader.read("PriorAuthorityAggregate", UUID.randomUUID(), context)).isEmpty();
  }

  @Test
  void failsInsteadOfReturningPartialHistoryAndClosesStream() {
    IllegalStateException failure = new IllegalStateException("historical read failed");
    MessageStream<EventMessage> stream =
        spy(MessageStream.just(mock(EventMessage.class)).concatWith(MessageStream.failed(failure)));
    when(eventStore.transaction(context)).thenReturn(transaction);
    doReturn(stream).when(transaction).source(any(SourcingCondition.class));

    assertThatThrownBy(() -> reader.read("PriorAuthorityAggregate", UUID.randomUUID(), context))
        .isInstanceOf(CompletionException.class)
        .hasCause(failure);
    verify(stream, atLeastOnce()).close();
  }
}
