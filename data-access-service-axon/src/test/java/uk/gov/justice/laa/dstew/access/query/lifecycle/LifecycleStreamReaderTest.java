package uk.gov.justice.laa.dstew.access.query.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.eventsourcing.eventstore.jpa.AggregateEventEntry;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.command.EventTypes;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;

@ExtendWith(MockitoExtension.class)
class LifecycleStreamReaderTest {

  private static final UUID GENESIS = UUID.randomUUID();
  private static final Instant OCCURRED_AT = Instant.parse("2026-11-06T10:00:00Z");
  private static final String NOTE_TYPE = eventName(NoteCreatedEvent.class);
  private static final String UPDATED_TYPE = eventName(ApplicationUpdatedEvent.class);
  private static final byte[] METADATA = "{\"X-Service-Name\":\"CIVIL_APPLY\"}".getBytes();

  @Mock private LifecycleStreamRepository repository;
  @Mock private EventConverter converter;
  @Mock private EntityManager entityManager;
  @Mock private TypedQuery<AggregateEventEntry> query;

  private LifecycleStreamReader reader;

  @BeforeEach
  void setUp() {
    reader = new LifecycleStreamReader(repository, converter, entityManager);
  }

  @Test
  void givenEmptyStream_whenRead_thenReturnsNothingWithoutQueryingEvents() {
    when(repository.findAllByGenesisIdOrderByOccurredAtAscIdAsc(GENESIS)).thenReturn(List.of());

    assertThat(reader.read(GENESIS)).isEmpty();
    verifyNoInteractions(entityManager, converter);
  }

  @Test
  void givenOnlyPointersFromOtherSources_whenRead_thenReturnsNothingWithoutQueryingEvents() {
    when(repository.findAllByGenesisIdOrderByOccurredAtAscIdAsc(GENESIS))
        .thenReturn(List.of(pointer("remote-event", "another-service")));

    assertThat(reader.read(GENESIS)).isEmpty();
    verifyNoInteractions(entityManager, converter);
  }

  @Test
  void givenPointers_whenRead_thenDecodesLocalEventsInPointerOrder() {
    when(repository.findAllByGenesisIdOrderByOccurredAtAscIdAsc(GENESIS))
        .thenReturn(
            List.of(
                pointer("updated", LifecycleStreamProjection.LOCAL_SOURCE),
                pointer("remote-event", "another-service"),
                pointer("note", LifecycleStreamProjection.LOCAL_SOURCE)));
    givenStoredEntries(
        List.of("updated", "note"),
        entry("note", NOTE_TYPE, METADATA),
        entry("updated", UPDATED_TYPE, METADATA));
    when(converter.convert(same(METADATA), any(Type.class)))
        .thenReturn(Map.of("X-Service-Name", "CIVIL_APPLY"));
    var note = new NoteCreatedEvent(GENESIS, 1L, OCCURRED_AT);
    var updated = new ApplicationUpdatedEvent(GENESIS, 2L, 2L, "A", "B", OCCURRED_AT);
    when(converter.convertEvent(any(EventMessage.class), eq(NoteCreatedEvent.class)))
        .thenAnswer(invocation -> decoded(invocation.getArgument(0), note));
    when(converter.convertEvent(any(EventMessage.class), eq(ApplicationUpdatedEvent.class)))
        .thenAnswer(invocation -> decoded(invocation.getArgument(0), updated));

    var events = reader.read(GENESIS);

    assertThat(events).extracting(EventMessage::identifier).containsExactly("updated", "note");
    assertThat(events).extracting(EventMessage::payload).containsExactly(updated, note);
    assertThat(events)
        .allSatisfy(
            event -> {
              assertThat(event.metadata()).containsEntry("X-Service-Name", "CIVIL_APPLY");
              assertThat(event.timestamp()).isEqualTo(OCCURRED_AT);
              assertThat(event.type().version()).isEqualTo("1.0");
            });
    assertThat(events.get(1).type().name()).isEqualTo(NOTE_TYPE);
  }

  @Test
  void givenStoredEventWithoutMetadata_whenRead_thenDecodesWithEmptyMetadata() {
    when(repository.findAllByGenesisIdOrderByOccurredAtAscIdAsc(GENESIS))
        .thenReturn(List.of(pointer("note", LifecycleStreamProjection.LOCAL_SOURCE)));
    givenStoredEntries(List.of("note"), entry("note", NOTE_TYPE, null));
    var note = new NoteCreatedEvent(GENESIS, 1L, OCCURRED_AT);
    when(converter.convertEvent(any(EventMessage.class), eq(NoteCreatedEvent.class)))
        .thenAnswer(invocation -> decoded(invocation.getArgument(0), note));

    var events = reader.read(GENESIS);

    assertThat(events).singleElement().satisfies(event -> assertThat(event.metadata()).isEmpty());
    verify(converter, never()).convert(any(), any(Type.class));
  }

  @Test
  void givenPointerToMissingEvent_whenRead_thenThrowsMissingStoredEvent() {
    when(repository.findAllByGenesisIdOrderByOccurredAtAscIdAsc(GENESIS))
        .thenReturn(
            List.of(
                pointer("note", LifecycleStreamProjection.LOCAL_SOURCE),
                pointer("missing", LifecycleStreamProjection.LOCAL_SOURCE)));
    givenStoredEntries(List.of("note", "missing"), entry("note", NOTE_TYPE, null));
    when(converter.convertEvent(any(EventMessage.class), eq(NoteCreatedEvent.class)))
        .thenAnswer(
            invocation ->
                decoded(invocation.getArgument(0), new NoteCreatedEvent(GENESIS, 1L, OCCURRED_AT)));

    assertThatThrownBy(() -> reader.read(GENESIS))
        .isInstanceOfSatisfying(
            MissingStoredEventException.class,
            exception -> {
              assertThat(exception.getGenesisId()).isEqualTo(GENESIS);
              assertThat(exception.getEventIdentifier()).isEqualTo("missing");
              assertThat(exception.getMessage()).contains(GENESIS.toString(), "missing");
            });
  }

  @Test
  void givenStoredEventOfUnknownType_whenRead_thenThrows() {
    when(repository.findAllByGenesisIdOrderByOccurredAtAscIdAsc(GENESIS))
        .thenReturn(List.of(pointer("unknown", LifecycleStreamProjection.LOCAL_SOURCE)));
    givenStoredEntries(List.of("unknown"), entry("unknown", "access.NoSuchEvent", null));

    assertThatThrownBy(() -> reader.read(GENESIS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("access.NoSuchEvent");
  }

  @Test
  void givenCommandPackage_whenScanned_thenFindsEveryEventTheStreamRecords() {
    Set<Class<?>> recorded =
        Arrays.stream(LifecycleStreamProjection.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(EventHandler.class))
            .map(method -> method.getParameterTypes()[0])
            .collect(Collectors.toSet());

    var types = EventTypes.all();

    assertThat(recorded).isNotEmpty();
    assertThat(types.values()).containsAll(recorded);
  }

  @Test
  void givenStoredKeptFieldsOnly_whenReadWithEventJsonConverter_thenDecodesWithContentErased() {
    var json = new EventJson();
    var priorAuthorityId = UUID.randomUUID();
    var written =
        new PriorAuthorityDecisionMadeEvent(
            priorAuthorityId,
            GENESIS,
            "EXPERT",
            3L,
            "GRANTED",
            "justified",
            new BigDecimal("125.50"),
            OCCURRED_AT,
            OCCURRED_AT);
    String type = eventName(PriorAuthorityDecisionMadeEvent.class);
    var kept =
        new EventContentSeparator(json, EventTypes.all())
            .separate(type, (ObjectNode) json.mapper().valueToTree(written))
            .kept();
    when(repository.findAllByGenesisIdOrderByOccurredAtAscIdAsc(GENESIS))
        .thenReturn(List.of(pointer("decision", LifecycleStreamProjection.LOCAL_SOURCE)));
    givenStoredEntries(
        List.of("decision"),
        entry("decision", type, json.mapper().writeValueAsBytes(kept), METADATA));
    var realReader =
        new LifecycleStreamReader(
            repository,
            new DelegatingEventConverter(new JacksonConverter(json.mapper())),
            entityManager);

    var events = realReader.read(GENESIS);

    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.payload())
                  .isEqualTo(
                      new PriorAuthorityDecisionMadeEvent(
                          priorAuthorityId,
                          GENESIS,
                          "EXPERT",
                          3L,
                          "GRANTED",
                          Erasable.erased(),
                          Erasable.erased(),
                          Erasable.erased(),
                          OCCURRED_AT,
                          Erasable.erased(),
                          Erasable.erased(),
                          Erasable.erased(),
                          Erasable.erased()));
              assertThat(event.metadata()).containsEntry("X-Service-Name", "CIVIL_APPLY");
            });
  }

  private void givenStoredEntries(List<String> identifiers, AggregateEventEntry... entries) {
    when(entityManager.createQuery(any(String.class), eq(AggregateEventEntry.class)))
        .thenReturn(query);
    when(query.setParameter("identifiers", identifiers)).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of(entries));
  }

  private static LifecycleStreamEntry pointer(String identifier, String source) {
    return LifecycleStreamEntry.builder()
        .genesisId(GENESIS)
        .source(source)
        .eventIdentifier(identifier)
        .eventName(NOTE_TYPE)
        .occurredAt(OCCURRED_AT)
        .build();
  }

  private static AggregateEventEntry entry(String identifier, String type, byte[] metadata) {
    return entry(identifier, type, "{}".getBytes(), metadata);
  }

  private static AggregateEventEntry entry(
      String identifier, String type, byte[] payload, byte[] metadata) {
    return new AggregateEventEntry(
        identifier,
        type,
        "1.0",
        payload,
        metadata,
        OCCURRED_AT,
        "ApplicationAggregate",
        GENESIS.toString(),
        0L);
  }

  private static EventMessage decoded(EventMessage stored, Object payload) {
    assertThat(stored.payload()).isEqualTo("{}".getBytes());
    return new GenericEventMessage(
        stored.identifier(), stored.type(), payload, stored.metadata(), stored.timestamp());
  }

  private static String eventName(Class<?> type) {
    return new AnnotationMessageTypeResolver().resolveOrThrow(type).name();
  }
}
