package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.GenericTaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
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
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.eventcontent.ContentTestEvent;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
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
    EventMessage event = message(new ContentTestEvent(id, Erasable.of("secret")));
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
              assertThat(row.tags()).containsExactly("applicationId=a-1");
              assertThat(row.content().get("note").asString()).isEqualTo("secret");
            });
    ArgumentCaptor<List<TaggedEventMessage<?>>> appended = ArgumentCaptor.forClass(List.class);
    verify(delegate).appendEvents(any(), any(), appended.capture());
    ContentTestEvent stored =
        converter.convert(appended.getValue().getFirst().event().payload(), ContentTestEvent.class);
    assertThat(stored.id()).isEqualTo(id);
    assertThat(stored.note().isErased()).isTrue();
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
  void givenStoredEvents_whenSourced_thenContentIsRejoinedWhereItExists() {
    String id = UUID.randomUUID().toString();
    EventMessage withContent = keptOnly(new ContentTestEvent(id, Erasable.of("secret")));
    EventMessage erased = keptOnly(new ContentTestEvent(id, null));
    EventMessage keptFieldsOnly = new GenericEventMessage(new MessageType(ASSIGNED), new byte[0]);
    EventMessage empty = new GenericEventMessage(new MessageType(ContentTestEvent.TYPE), null);
    ObjectNode content = json.mapper().createObjectNode().put("note", "secret");
    when(store.findByTags(Set.of("applicationId=a-1")))
        .thenReturn(
            Map.of(
                withContent.identifier(),
                content,
                erased.identifier(),
                json.mapper().createObjectNode(),
                empty.identifier(),
                content));
    SourcingCondition condition = SourcingCondition.conditionFor(EventCriteria.havingTags(TAG));
    when(delegate.source(condition, null))
        .thenReturn(MessageStream.fromItems(withContent, erased, keptFieldsOnly, empty));

    MessageStream<EventMessage> sourced = engine.source(condition, null);

    assertThat(note(sourced.next().orElseThrow().message())).isEqualTo(Erasable.of("secret"));
    assertThat(note(sourced.next().orElseThrow().message()).isErased()).isTrue();
    assertThat(sourced.next().orElseThrow().message()).isSameAs(keptFieldsOnly);
    assertThat(sourced.next().orElseThrow().message().payload()).isNull();
    verify(store, never()).findByEventId(any());
  }

  @Test
  void givenEventCommittedAfterTheTagRead_whenSourced_thenItsContentIsFoundByEventId() {
    EventMessage later =
        keptOnly(new ContentTestEvent(UUID.randomUUID().toString(), Erasable.of("later")));
    when(store.findByTags(Set.of("applicationId=a-1"))).thenReturn(Map.of());
    when(store.findByEventId(later.identifier()))
        .thenReturn(Optional.of(json.mapper().createObjectNode().put("note", "later")));
    SourcingCondition condition = SourcingCondition.conditionFor(EventCriteria.havingTags(TAG));
    when(delegate.source(condition, null)).thenReturn(MessageStream.fromItems(later));

    MessageStream<EventMessage> sourced = engine.source(condition, null);

    assertThat(note(sourced.next().orElseThrow().message())).isEqualTo(Erasable.of("later"));
  }

  @Test
  void givenEventWithContentFieldsAndNoContentRow_whenSourced_thenFails() {
    EventMessage lost =
        keptOnly(new ContentTestEvent(UUID.randomUUID().toString(), Erasable.of("lost")));
    when(store.findByTags(Set.of("applicationId=a-1"))).thenReturn(Map.of());
    when(store.findByEventId(lost.identifier())).thenReturn(Optional.empty());
    SourcingCondition condition = SourcingCondition.conditionFor(EventCriteria.havingTags(TAG));
    when(delegate.source(condition, null)).thenReturn(MessageStream.fromItems(lost));

    MessageStream<EventMessage> sourced = engine.source(condition, null);

    assertThatThrownBy(sourced::next)
        .isInstanceOf(MissingEventContentException.class)
        .hasMessage(
            "Event "
                + lost.identifier()
                + " of type "
                + ContentTestEvent.TYPE
                + " has no content row");
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

  private EventMessage keptOnly(ContentTestEvent event) {
    ObjectNode payload = (ObjectNode) json.mapper().valueToTree(event);
    byte[] kept =
        converter.convert(separator.separate(ContentTestEvent.TYPE, payload).kept(), byte[].class);
    return message(kept);
  }

  private Erasable<String> note(EventMessage message) {
    return converter.convert(message.payload(), ContentTestEvent.class).note();
  }

  private static TaggedEventMessage<?> tagged(EventMessage event) {
    return new GenericTaggedEventMessage<>(event, Set.of(TAG));
  }
}
