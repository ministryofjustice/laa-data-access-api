package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import static org.assertj.core.api.Assertions.assertThat;

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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityEvolve;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;

/**
 * Guards events already in the store against schema change. Each fixture under {@code
 * stored-events/<event type>/} is an event as stored, its kept fields and its content, captured
 * from a real append. Every stored version must still load unchanged into the current event class.
 * Without upcasting, a renamed, added or removed field would otherwise load silently as erased or
 * null, and an unknown stored field would be dropped. When this fails, add an upcaster for the old
 * version and a fixture for the new one, rather than editing the old fixture.
 */
class StoredEventCompatibilityTest {

  private static final String FIXTURES = "stored-events/";
  private static final Map<String, Class<?>> EVENT_TYPES =
      AxonStoredEvents.eventTypesIn("uk.gov.justice.laa.dstew.access.command");
  private static final UUID PRIOR_AUTHORITY_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID DOCUMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

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
  void givenStoredEvent_whenLoadedWithCurrentSchema_thenLoadsUnchanged(Fixture fixture) {
    Class<?> type = EVENT_TYPES.get(fixture.typeName());
    ObjectNode whole = separator.rejoin(fixture.kept(), fixture.content());

    assertThat(new TreeSet<>(whole.propertyNames()))
        .as("stored fields of %s against the current record components", fixture)
        .isEqualTo(componentNames(type));

    EventContentSeparator.Split split = separator.separate(fixture.typeName(), whole);
    assertThat(split.kept()).as("kept fields of %s", fixture).isEqualTo(fixture.kept());
    assertThat(split.content()).as("content of %s", fixture).isEqualTo(fixture.content());

    Object event = mapper.treeToValue(whole, type);
    // Re-read both sides so number node types (int or long) do not affect equality.
    assertThat(mapper.readTree(mapper.writeValueAsString(event)))
        .as("%s re-serialised", fixture)
        .isEqualTo(mapper.readTree(whole.toString()));
  }

  @Test
  void givenStoredPriorAuthorityHistory_whenFolded_thenAggregateViewIsComplete() {
    PriorAuthorityState state = new PriorAuthorityState();
    PriorAuthorityEvolve.apply(
        state, load("access.PriorAuthorityDraftStarted", PriorAuthorityDraftStartedEvent.class));
    PriorAuthorityEvolve.apply(
        state,
        load("access.PriorAuthorityDocumentUploaded", PriorAuthorityDocumentUploadedEvent.class));
    PriorAuthorityEvolve.apply(
        state, load("access.PriorAuthoritySubmitted", PriorAuthoritySubmittedEvent.class));
    PriorAuthorityEvolve.apply(
        state, load("access.PriorAuthorityDecisionMade", PriorAuthorityDecisionMadeEvent.class));

    assertThat(state.status()).isEqualTo(PriorAuthorityStatus.DECIDED);
    assertThat(state.getPriorAuthorityVersion()).isEqualTo(1L);
    assertThat(state.payload())
        .hasValueSatisfying(
            payload -> {
              assertThat(payload.priorAuthorityId()).isEqualTo(PRIOR_AUTHORITY_ID);
              assertThat(payload.officeCode()).isEqualTo("1A001B");
              assertThat(payload.content().justification())
                  .isEqualTo("Interpreter costs for proceedings");
              assertThat(payload.serialisedRequest()).contains("Court interpreter");
              assertThat(payload.documentFilenames())
                  .containsExactly(Map.entry(DOCUMENT_ID, "existing.pdf"));
              assertThat(payload.decisionDetails().decision()).isEqualTo("GRANTED");
              assertThat(payload.decisionDetails().decisionJustification())
                  .isEqualTo("Granted as per policy guidelines");
              assertThat(payload.decisionDetails().amountGranted())
                  .isEqualTo(new BigDecimal("150.0"));
            });
  }

  private <T> T load(String typeName, Class<T> type) {
    Fixture fixture =
        fixtures()
            .filter(f -> f.typeName().equals(typeName) && f.version().equals("v1"))
            .findFirst()
            .orElseThrow();
    return mapper.treeToValue(separator.rejoin(fixture.kept(), fixture.content()), type);
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
          (ObjectNode) stored.get("content"));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Set<String> componentNames(Class<?> type) {
    return Arrays.stream(type.getRecordComponents())
        .map(RecordComponent::getName)
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
