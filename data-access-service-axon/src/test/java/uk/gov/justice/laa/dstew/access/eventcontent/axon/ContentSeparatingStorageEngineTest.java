package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.GenericTaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.GlobalIndexConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.TerminalEventMessage;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.eventstreaming.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.eventcontent.ContentTestEvent;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore.ContentRow;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;
import uk.gov.justice.laa.dstew.access.eventcontent.MissingEventContentException;

class ContentSeparatingStorageEngineTest {

  private static final Tag TAG = Tag.of("applicationId", "a-1");
  private static final String ASSIGNED = "access.WorkItemAssigned";

  private final EventJson json = new EventJson();
  private final EventConverter converter =
      new DelegatingEventConverter(new JacksonConverter(json.mapper()));
  private final EventContentSeparator separator =
      new EventContentSeparator(
          json,
          Map.of(ContentTestEvent.TYPE, ContentTestEvent.class, ASSIGNED, WorkItemAssigned.class));
  private final EventStorageEngine delegate = mock(EventStorageEngine.class);
  private final EventContentStore store = mock(EventContentStore.class);
  private final ContentSeparatingStorageEngine engine =
      new ContentSeparatingStorageEngine(delegate, () -> separator, () -> store, () -> converter);

  @Test
  @SuppressWarnings("unchecked")
  void givenEventWithContent_whenAppended_thenContentIsStoredAndOnlyKeptFieldsAreAppended() {
    String id = UUID.randomUUID().toString();
    EventMessage event = message(new ContentTestEvent(id, "secret"));
    when(delegate.appendEvents(any(), any(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(null));

    engine.appendEvents(AppendCondition.none(), null, List.of(tagged(event)));

    ArgumentCaptor<List<ContentRow>> rows = ArgumentCaptor.forClass(List.class);
    verify(store).save(rows.capture());
    assertThat(rows.getValue())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.eventId()).isEqualTo(event.identifier());
              assertThat(row.content().get("note").asString()).isEqualTo("secret");
            });
    ArgumentCaptor<List<TaggedEventMessage<?>>> appended = ArgumentCaptor.forClass(List.class);
    verify(delegate).appendEvents(any(), any(), appended.capture());
    ContentTestEvent stored =
        converter.convert(appended.getValue().getFirst().event().payload(), ContentTestEvent.class);
    assertThat(stored.id()).isEqualTo(id);
    assertThat(stored.note()).isNull();
  }

  @Test
  void givenEventWithoutContent_whenAppended_thenNothingIsStored() {
    EventMessage event = message(Map.of("id", UUID.randomUUID().toString()));

    engine.appendEvents(AppendCondition.none(), null, List.of(tagged(event)));

    verifyNoInteractions(store);
    verify(delegate).appendEvents(any(), any(), anyList());
  }

  @Test
  void givenUnclassifiedEvent_whenAppended_thenAppendFailsWithoutWriting() {
    EventMessage event =
        new GenericEventMessage(new MessageType("test.Missing"), Map.of("id", "x"));

    CompletableFuture<?> result =
        engine.appendEvents(AppendCondition.none(), null, List.of(tagged(event)));

    assertThat(result).isCompletedExceptionally();
    verifyNoInteractions(store);
    verify(delegate, never()).appendEvents(any(), any(), anyList());
  }

  @Test
  void givenStoredEvents_whenSourced_thenContentIsRejoinedByEventIdInOneLookup() {
    EventMessage withContent = keptOnly(UUID.randomUUID().toString(), "secret");
    EventMessage erased = keptOnly(UUID.randomUUID().toString(), "gone");
    EventMessage keptFieldsOnly = new GenericEventMessage(new MessageType(ASSIGNED), new byte[0]);
    List<String> contentEventIds = List.of(withContent.identifier(), erased.identifier());
    when(store.findByEventIds(contentEventIds))
        .thenReturn(
            Map.of(
                withContent.identifier(),
                json.mapper().createObjectNode().put("note", "secret"),
                erased.identifier(),
                json.mapper().createObjectNode()));
    SourcingCondition condition = SourcingCondition.conditionFor(EventCriteria.havingTags(TAG));
    when(delegate.source(condition, null))
        .thenReturn(MessageStream.fromItems(withContent, erased, keptFieldsOnly));

    MessageStream<EventMessage> sourced = engine.source(condition, null);

    assertThat(payload(sourced.next().orElseThrow().message()).get("note").asString())
        .isEqualTo("secret");
    assertThat(payload(sourced.next().orElseThrow().message()).has("note")).isFalse();
    assertThat(sourced.next().orElseThrow().message()).isSameAs(keptFieldsOnly);
    verify(store).findByEventIds(contentEventIds);
    verifyNoMoreInteractions(store);
  }

  @Test
  void givenTerminalEntry_whenSourced_thenItAndItsContextArePreserved() {
    EventMessage event = keptOnly(UUID.randomUUID().toString(), "secret");
    ConsistencyMarker marker = new GlobalIndexConsistencyMarker(42);
    when(store.findByEventIds(List.of(event.identifier())))
        .thenReturn(
            Map.of(event.identifier(), json.mapper().createObjectNode().put("note", "secret")));
    SourcingCondition condition = SourcingCondition.conditionFor(EventCriteria.havingTags(TAG));
    when(delegate.source(condition, null))
        .thenReturn(
            MessageStream.<EventMessage>fromItems(event)
                .concatWith(
                    MessageStream.just(
                        TerminalEventMessage.INSTANCE,
                        terminal -> Context.with(ConsistencyMarker.RESOURCE_KEY, marker))));

    MessageStream<EventMessage> sourced = engine.source(condition, null);

    assertThat(payload(sourced.next().orElseThrow().message()).get("note").asString())
        .isEqualTo("secret");
    var last = sourced.next().orElseThrow();
    assertThat(last.message()).isSameAs(TerminalEventMessage.INSTANCE);
    assertThat(last.getResource(ConsistencyMarker.RESOURCE_KEY)).isSameAs(marker);
    assertThat(sourced.next()).isEmpty();
  }

  @Test
  void givenEventWithContentFieldsAndNoContentRow_whenSourced_thenFails() {
    EventMessage lost = keptOnly(UUID.randomUUID().toString(), "lost");
    SourcingCondition condition = SourcingCondition.conditionFor(EventCriteria.havingTags(TAG));
    when(delegate.source(condition, null)).thenReturn(MessageStream.fromItems(lost));

    MessageStream<EventMessage> sourced = engine.source(condition, null);

    assertThat(sourced.error())
        .hasValueSatisfying(
            error ->
                assertThat(error)
                    .isInstanceOf(MissingEventContentException.class)
                    .hasMessage(
                        "Event "
                            + lost.identifier()
                            + " of type "
                            + ContentTestEvent.TYPE
                            + " has no content row"));
  }

  @Test
  void givenEventsWithoutContent_whenSourced_thenNoContentIsLookedUp() {
    EventMessage keptFieldsOnly = new GenericEventMessage(new MessageType(ASSIGNED), new byte[0]);
    EventMessage empty = new GenericEventMessage(new MessageType(ContentTestEvent.TYPE), null);
    SourcingCondition condition = SourcingCondition.conditionFor(EventCriteria.havingTags(TAG));
    when(delegate.source(condition, null))
        .thenReturn(MessageStream.fromItems(keptFieldsOnly, empty));

    MessageStream<EventMessage> sourced = engine.source(condition, null);

    assertThat(sourced.next().orElseThrow().message()).isSameAs(keptFieldsOnly);
    assertThat(sourced.next().orElseThrow().message()).isSameAs(empty);
    verifyNoInteractions(store);
  }

  @Test
  void givenProcessorStream_whenRequested_thenPassedThroughUnchanged() {
    var stream = MessageStream.<EventMessage>empty();
    var token = CompletableFuture.completedFuture(mock(TrackingToken.class));
    var at = Instant.now();
    StreamingCondition condition = StreamingCondition.startingFrom(null);
    when(delegate.stream(condition)).thenReturn(stream);
    when(delegate.firstToken()).thenReturn(token);
    when(delegate.latestToken()).thenReturn(token);
    when(delegate.tokenAt(at)).thenReturn(token);
    ComponentDescriptor descriptor = mock(ComponentDescriptor.class);

    assertThat(engine.stream(condition)).isSameAs(stream);
    assertThat(engine.firstToken()).isSameAs(token);
    assertThat(engine.latestToken()).isSameAs(token);
    assertThat(engine.tokenAt(at)).isSameAs(token);
    engine.describeTo(descriptor);
    verify(descriptor).describeWrapperOf(delegate);
  }

  private EventMessage message(Object payload) {
    return new GenericEventMessage(new MessageType(ContentTestEvent.TYPE), payload);
  }

  private EventMessage keptOnly(String id, String note) {
    ObjectNode payload = json.mapper().createObjectNode().put("id", id).put("note", note);
    byte[] kept =
        converter.convert(separator.separate(ContentTestEvent.TYPE, payload).kept(), byte[].class);
    return message(kept);
  }

  private JsonNode payload(EventMessage message) {
    return converter.convert(message.payload(), JsonNode.class);
  }

  private static TaggedEventMessage<?> tagged(EventMessage event) {
    return new GenericTaggedEventMessage<>(event, Set.of(TAG));
  }
}
