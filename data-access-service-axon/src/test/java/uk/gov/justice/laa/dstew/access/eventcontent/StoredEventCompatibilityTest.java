package uk.gov.justice.laa.dstew.access.eventcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.command.EventTypes;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationEvolve;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityEvolve;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

/**
 * Guards events already in the store against change. Each fixture under {@code stored-events/<event
 * type>/vN.json} is one event as stored: its kept payload row and its content row ({@code null}
 * when the type has no content fields). They were captured from real appends with synthetic data,
 * and are never edited.
 *
 * <p>Without upcasting, a stored row still decodes when the event record changes: an unknown stored
 * field is ignored and a missing one, even a required kept field, loads silently as null or erased.
 * Only a changed field type or a missing primitive fails. So these fixtures are what notices a
 * changed stored shape.
 *
 * <p>Every stored value must survive loading, at any depth. A field added to an event or to a class
 * it carries passes, because older events simply lack it: it loads as null, erased or its default,
 * and readers must accept that. A renamed, removed or retyped field fails, because stored data
 * would be lost. When this test fails, add an upcaster for the old version and a new {@code
 * vN.json} for the new shape; every older version must still load.
 */
class StoredEventCompatibilityTest {

  private static final String FIXTURES = "stored-events/";
  private static final Map<String, Class<?>> EVENT_TYPES = EventTypes.all();
  private static final UUID APPLICATION_ID = id(1);
  private static final UUID PROCEEDING_ID = id(2);
  private static final UUID APPLICATION_DOCUMENT_ID = id(4);
  private static final UUID PRIOR_AUTHORITY_ID = id(12);
  private static final UUID PRIOR_AUTHORITY_DOCUMENT_ID = id(14);
  private static final UUID CASEWORKER_ID = id(15);

  private final ObjectMapper mapper = new EventJson().mapper();
  private final EventContentSeparator separator =
      new EventContentSeparator(new EventJson(), EVENT_TYPES);

  record Fixture(String typeName, String version, ObjectNode kept, ObjectNode content) {
    @Override
    public String toString() {
      return typeName + " " + version;
    }
  }

  @Test
  void givenEveryEventType_whenFixturesListed_thenEachHasStoredFixture() {
    Set<String> covered = fixtures().map(Fixture::typeName).collect(Collectors.toSet());

    assertThat(covered).containsExactlyInAnyOrderElementsOf(EVENT_TYPES.keySet());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void givenStoredEvent_whenLoadedWithCurrentRecord_thenNoStoredValueIsLost(Fixture fixture) {
    assertLoadsWithoutLoss(fixture);
  }

  @Test
  void givenStoredEventWithoutKeptFieldAddedLater_whenLoaded_thenAccepted() {
    Fixture fixture = fixture("access.ApplicationCreated");
    fixture.kept().remove("officeCode");

    assertThatCode(() -> assertLoadsWithoutLoss(fixture)).doesNotThrowAnyException();
  }

  @Test
  void givenStoredEventWithoutNestedContentFieldAddedLater_whenLoaded_thenAccepted() {
    Fixture fixture = fixture("access.ApplicationCreated");
    client(fixture).remove("lastNameAtBirth");

    assertThatCode(() -> assertLoadsWithoutLoss(fixture)).doesNotThrowAnyException();
  }

  @Test
  void givenStoredFieldRenamed_whenLoaded_thenRejected() {
    Fixture fixture = fixture("access.NoteCreated");
    fixture.content().set("text", fixture.content().remove("noteText"));

    assertThatThrownBy(() -> assertLoadsWithoutLoss(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("text");
  }

  @Test
  void givenStoredNestedFieldRenamed_whenLoaded_thenRejected() {
    Fixture fixture = fixture("access.ApplicationCreated");
    ObjectNode client = client(fixture);
    client.set("forename", client.remove("firstName"));

    assertThatThrownBy(() -> assertLoadsWithoutLoss(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("forename");
  }

  @Test
  void givenStoredFieldRetyped_whenLoaded_thenRejected() {
    Fixture fixture = fixture("access.ApplicationCreated");
    client(fixture).putObject("dateOfBirth").put("year", 2000);

    assertThatThrownBy(() -> assertLoadsWithoutLoss(fixture)).isNotNull();
  }

  private void assertLoadsWithoutLoss(Fixture fixture) {
    Class<?> type = EVENT_TYPES.get(fixture.typeName());
    assertThat(type).as("event class for %s", fixture).isNotNull();
    assertThat(fixture.content() != null)
        .as("%s has a content row exactly when its type has content fields", fixture)
        .isEqualTo(separator.hasContentFields(fixture.typeName()));
    ObjectNode whole = whole(fixture);

    assertThat(componentNames(type))
        .as("current record components of %s against its stored fields", fixture)
        .containsAll(whole.propertyNames());

    Object event = mapper.treeToValue(whole, type);
    // Re-splitting the loaded event checks each stored field is still kept or content as stored.
    // Re-read both sides so number node types (int or long) do not affect equality.
    ObjectNode loaded = (ObjectNode) mapper.readTree(mapper.writeValueAsString(event));
    EventContentSeparator.Split split = separator.separate(fixture.typeName(), loaded);
    assertPreserved(fixture + " kept", mapper.readTree(fixture.kept().toString()), split.kept());
    assertPreserved(
        fixture + " content",
        fixture.content() == null
            ? mapper.createObjectNode()
            : mapper.readTree(fixture.content().toString()),
        split.content());
  }

  /** Every stored value is present and equal after loading; fields added since may also appear. */
  private static void assertPreserved(String path, JsonNode stored, JsonNode loaded) {
    if (stored.isObject() && loaded != null && loaded.isObject()) {
      for (String name : stored.propertyNames()) {
        assertThat(loaded.has(name))
            .as("%s.%s is stored but no longer loaded (renamed or removed?)", path, name)
            .isTrue();
        assertPreserved(path + "." + name, stored.get(name), loaded.get(name));
      }
    } else if (stored.isArray() && loaded != null && loaded.isArray()) {
      assertThat(loaded.size()).as("%s size", path).isEqualTo(stored.size());
      for (int i = 0; i < stored.size(); i++) {
        assertPreserved(path + "[" + i + "]", stored.get(i), loaded.get(i));
      }
    } else {
      assertThat(loaded).as("%s re-serialised", path).isEqualTo(stored);
    }
  }

  private static Fixture fixture(String typeName) {
    Fixture fixture =
        fixtures()
            .filter(f -> f.typeName().equals(typeName) && f.version().equals("v1"))
            .findFirst()
            .orElseThrow();
    return new Fixture(
        fixture.typeName(),
        fixture.version(),
        fixture.kept().deepCopy(),
        fixture.content() == null ? null : fixture.content().deepCopy());
  }

  private static ObjectNode client(Fixture fixture) {
    return (ObjectNode) fixture.content().get("content").get("client");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixturesWithContent")
  void givenStoredEventWithErasedContentRow_whenLoaded_thenContentFieldsAreErased(Fixture fixture)
      throws ReflectiveOperationException {
    Class<?> type = EVENT_TYPES.get(fixture.typeName());

    Object erased = mapper.treeToValue(separator.rejoin(fixture.kept(), emptyRow()), type);

    assertThat(erased)
        .as("%s with an erased content row", fixture)
        .isEqualTo(separator.erase(fixture.typeName(), mapper.treeToValue(whole(fixture), type)));
    for (String field : fixture.content().propertyNames()) {
      Object value = type.getMethod(field).invoke(erased);
      assertThat(value == null || value instanceof Erasable<?> erasable && erasable.isErased())
          .as("%s.%s erased", fixture, field)
          .isTrue();
    }
    assertThatThrownBy(() -> separator.requireNoContentFields(fixture.typeName(), "event-id"))
        .as("%s without a content row", fixture)
        .isInstanceOf(MissingEventContentException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixturesWithoutContent")
  void givenStoredEventOfContentFreeType_whenLoadedWithoutContentRow_thenAccepted(Fixture fixture) {
    assertThatCode(() -> separator.requireNoContentFields(fixture.typeName(), "event-id"))
        .doesNotThrowAnyException();
  }

  @Test
  void givenStoredPriorAuthorityHistory_whenFolded_thenDecidedAggregateIsComplete() {
    PriorAuthorityState state = new PriorAuthorityState();
    PriorAuthorityEvolve.apply(state, load(PriorAuthorityDraftStartedEvent.class));
    PriorAuthorityEvolve.apply(state, load(PriorAuthorityDraftUpdatedEvent.class));
    PriorAuthorityEvolve.apply(state, load(PriorAuthorityDocumentUploadedEvent.class));
    PriorAuthorityEvolve.apply(state, load(PriorAuthorityDocumentTypeUpdatedEvent.class));
    // The deleted document is a second upload, which has no fixture of its own.
    PriorAuthorityEvolve.apply(state, load(PriorAuthorityDocumentDeletedEvent.class));

    assertThat(state.draft())
        .hasValueSatisfying(
            draft -> {
              assertThat(draft.content().justification()).isEqualTo("Updated interpreter costs");
              assertThat(draft.documentFilenames())
                  .containsExactly(Map.entry(PRIOR_AUTHORITY_DOCUMENT_ID, "evidence.pdf"));
            });

    PriorAuthorityEvolve.apply(state, load(PriorAuthoritySubmittedEvent.class));
    PriorAuthorityEvolve.apply(state, load(WorkItemAssigned.class));
    assertThat(state.getCaseworkerId()).isEqualTo(CASEWORKER_ID);
    PriorAuthorityEvolve.apply(state, load(WorkItemUnassigned.class));
    PriorAuthorityEvolve.apply(state, load(PriorAuthorityDecisionMadeEvent.class));

    assertThat(state.getPriorAuthorityId()).isEqualTo(PRIOR_AUTHORITY_ID);
    assertThat(state.getApplicationId()).isEqualTo(APPLICATION_ID);
    assertThat(state.getOfficeCode()).isEqualTo("1A001B");
    assertThat(state.isSubmitted()).isTrue();
    assertThat(state.isDecided()).isTrue();
    assertThat(state.getDataVersion()).isEqualTo(1L);
    assertThat(state.getCaseworkerId()).isNull();
    assertThat(state.getAssignmentVersion()).isEqualTo(2L);
    assertThat(state.getUploadedDocuments())
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document.documentId()).isEqualTo(PRIOR_AUTHORITY_DOCUMENT_ID);
              assertThat(document.documentType()).isEqualTo("GATEWAY_EVIDENCE");
            });
    assertThat(state.submittedPayload())
        .hasValueSatisfying(
            payload -> {
              assertThat(payload.priorAuthorityId()).isEqualTo(PRIOR_AUTHORITY_ID);
              assertThat(payload.officeCode()).isEqualTo("1A001B");
              assertThat(payload.content().justification()).isEqualTo("Updated interpreter costs");
              assertThat(payload.documentFilenames())
                  .containsExactly(Map.entry(PRIOR_AUTHORITY_DOCUMENT_ID, "evidence.pdf"));
              assertThat(payload.decisionDetails().decision()).isEqualTo("GRANTED");
              assertThat(payload.decisionDetails().decisionJustification())
                  .isEqualTo("Granted as per policy guidelines");
              assertThat(payload.decisionDetails().amountGranted())
                  .isEqualTo(new BigDecimal("175.50"));
              assertThat(payload.decisionDetails().decisionSerialisedRequest())
                  .contains("\"decision\":\"GRANTED\"");
            });
  }

  @Test
  void givenStoredApplicationHistory_whenFolded_thenGrantedAggregateIsComplete() {
    ApplicationState state = new ApplicationState();
    ApplicationEvolve.apply(state, load(ApplicationDraftStartedEvent.class));
    ApplicationEvolve.apply(state, load(ApplicationDocumentUploadedEvent.class));
    ApplicationEvolve.apply(state, load(ApplicationCreatedEvent.class));
    ApplicationEvolve.apply(state, load(ApplicationUpdatedEvent.class));
    ApplicationEvolve.apply(state, load(NoteCreatedEvent.class));
    ApplicationEvolve.apply(state, load(ApplicationReadyForManualAssessmentEvent.class));
    ApplicationEvolve.apply(state, load(ApplicationDecisionMadeEvent.class));

    assertThat(state.getApplicationId()).isEqualTo(APPLICATION_ID);
    assertThat(state.getLaaReference()).isEqualTo("LAA-FIXTURE-1");
    assertThat(state.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_GRANTED.getValue());
    assertThat(state.getAutoGranted()).isEqualTo(AutoGrantedState.MANUAL);
    assertThat(state.getApplicationVersion()).isEqualTo(3L);
    assertThat(state.getApplicationDataVersion()).isEqualTo(4L);
    assertThat(state.getProceedingDecisions()).containsExactly(Map.entry(PROCEEDING_ID, "GRANTED"));
    assertThat(state.getUploadedDocuments())
        .singleElement()
        .satisfies(
            document -> assertThat(document.documentId()).isEqualTo(APPLICATION_DOCUMENT_ID));
    assertThat(state.getContent().isErased()).isFalse();
    var content = state.getContent().value();
    assertThat(content.laaReference()).isEqualTo("LAA-FIXTURE-1");
    assertThat(content.client().getFirstName()).isEqualTo("Fixture");
    assertThat(content.provider().getContactEmail()).isEqualTo("updated-provider@example.com");
    assertThat(content.documentFilenames())
        .containsExactly(Map.entry(APPLICATION_DOCUMENT_ID, "evidence.pdf"));
    assertThat(content.notes())
        .singleElement()
        .satisfies(note -> assertThat(note.noteText()).isEqualTo("Synthetic fixture note"));
    assertThat(content.overallDecision()).isEqualTo("GRANTED");
    assertThat(content.certificate()).containsEntry("certificateNumber", "FIXTURE-CERT-001");
    assertThat(content.meritsDecisions()).containsKey(PROCEEDING_ID);
    assertThat(content.decisionEventDescription()).isEqualTo("Synthetic decision");
  }

  private <T> T load(Class<T> type) {
    String typeName =
        EVENT_TYPES.entrySet().stream()
            .filter(entry -> entry.getValue().equals(type))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    Fixture fixture =
        fixtures()
            .filter(f -> f.typeName().equals(typeName) && f.version().equals("v1"))
            .findFirst()
            .orElseThrow();
    return mapper.treeToValue(whole(fixture), type);
  }

  private ObjectNode whole(Fixture fixture) {
    return separator.rejoin(
        fixture.kept(), fixture.content() == null ? emptyRow() : fixture.content());
  }

  private ObjectNode emptyRow() {
    return mapper.createObjectNode();
  }

  static Stream<Fixture> fixturesWithContent() {
    return fixtures().filter(fixture -> fixture.content() != null);
  }

  static Stream<Fixture> fixturesWithoutContent() {
    return fixtures().filter(fixture -> fixture.content() == null);
  }

  static Stream<Fixture> fixtures() {
    ObjectMapper mapper = new EventJson().mapper();
    try {
      Resource[] resources =
          new PathMatchingResourcePatternResolver()
              .getResources("classpath:" + FIXTURES + "*/*.json");
      return Arrays.stream(resources).map(resource -> read(mapper, resource));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Fixture read(ObjectMapper mapper, Resource resource) {
    try (InputStream in = resource.getInputStream()) {
      String path = resource.getURI().toString();
      String[] parts = path.substring(path.lastIndexOf(FIXTURES) + FIXTURES.length()).split("/");
      ObjectNode stored = (ObjectNode) mapper.readTree(in);
      return new Fixture(
          parts[0],
          parts[1].replace(".json", ""),
          (ObjectNode) stored.get("kept"),
          stored.get("content").isNull() ? null : (ObjectNode) stored.get("content"));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Set<String> componentNames(Class<?> type) {
    return Arrays.stream(type.getRecordComponents())
        .map(RecordComponent::getName)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static UUID id(int n) {
    return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
  }
}
