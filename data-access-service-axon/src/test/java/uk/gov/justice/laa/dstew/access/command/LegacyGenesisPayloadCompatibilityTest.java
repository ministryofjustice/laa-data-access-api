package uk.gov.justice.laa.dstew.access.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.axonframework.extension.springboot.autoconfig.JacksonConverterAutoConfiguration;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationEvolve;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityEvolve;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/**
 * Events stored before ADR 0006 lack the genesis field added to three event shapes. They must still
 * deserialize through Axon's configured event converter and fold exactly as before.
 */
class LegacyGenesisPayloadCompatibilityTest {

  private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PARENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID CASEWORKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID DOCUMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-01T10:00:00Z");

  private static final String LEGACY_ASSIGNED =
      """
      {"workItemId":"00000000-0000-0000-0000-000000000001","workItemType":"PRIOR_AUTHORITY",
       "itemVersion":0,"assignmentVersion":1,
       "caseworkerId":"00000000-0000-0000-0000-000000000003",
       "occurredAt":"2026-10-01T10:00:00Z"}
      """;
  private static final String LEGACY_UNASSIGNED =
      """
      {"workItemId":"00000000-0000-0000-0000-000000000001","workItemType":"PRIOR_AUTHORITY",
       "itemVersion":0,"assignmentVersion":2,"occurredAt":"2026-10-01T10:00:00Z"}
      """;
  private static final String LEGACY_DOCUMENT_TYPE_UPDATED =
      """
      {"priorAuthorityId":"00000000-0000-0000-0000-000000000001",
       "documentId":"00000000-0000-0000-0000-000000000004",
       "documentType":"INVOICE","occurredAt":"2026-10-01T10:00:00Z"}
      """;

  private static EventConverter converter;
  private static ObjectMapper objectMapper;

  /** Builds the converter exactly as the application does from {@code axon.converter.*}. */
  @BeforeAll
  static void configuredConverter() {
    AtomicReference<EventConverter> eventConverter = new AtomicReference<>();
    AtomicReference<ObjectMapper> mapper = new AtomicReference<>();
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class, JacksonConverterAutoConfiguration.class))
        .withPropertyValues(
            "axon.converter.general=jackson",
            "axon.converter.events=jackson",
            "axon.converter.messages=jackson")
        .run(
            context -> {
              eventConverter.set(context.getBean(EventConverter.class));
              mapper.set(context.getBean(ObjectMapper.class));
            });
    converter = eventConverter.get();
    objectMapper = mapper.get();
  }

  @Test
  void givenLegacyWorkItemAssigned_whenReplayed_thenFoldsLikeCurrentShape() {
    WorkItemAssigned current = assigned();
    assertIsLegacyShapeOf(LEGACY_ASSIGNED, current, "applicationId");

    WorkItemAssigned legacy = read(LEGACY_ASSIGNED, WorkItemAssigned.class);

    assertThat(legacy.applicationId()).isNull();
    assertThat(legacy.genesisIds()).isEmpty();
    assertThat(legacy)
        .usingRecursiveComparison()
        .ignoringFields("applicationId")
        .isEqualTo(current);
    assertThat(applicationState(legacy))
        .usingRecursiveComparison()
        .isEqualTo(applicationState(current));
    assertThat(priorAuthorityState(legacy))
        .usingRecursiveComparison()
        .isEqualTo(priorAuthorityState(current));
    assertThat(priorAuthorityState(legacy).getCaseworkerId()).isEqualTo(CASEWORKER_ID);
  }

  @Test
  void givenLegacyWorkItemUnassigned_whenReplayed_thenFoldsLikeCurrentShape() {
    WorkItemUnassigned current =
        new WorkItemUnassigned(
            ITEM_ID, WorkItemType.PRIOR_AUTHORITY, PARENT_ID, 0L, 2L, OCCURRED_AT);
    assertIsLegacyShapeOf(LEGACY_UNASSIGNED, current, "applicationId");

    WorkItemUnassigned legacy = read(LEGACY_UNASSIGNED, WorkItemUnassigned.class);

    assertThat(legacy.applicationId()).isNull();
    assertThat(legacy.genesisIds()).isEmpty();
    assertThat(legacy)
        .usingRecursiveComparison()
        .ignoringFields("applicationId")
        .isEqualTo(current);
    assertThat(applicationState(assigned(), legacy))
        .usingRecursiveComparison()
        .isEqualTo(applicationState(assigned(), current));
    assertThat(priorAuthorityState(assigned(), legacy))
        .usingRecursiveComparison()
        .isEqualTo(priorAuthorityState(assigned(), current));
    assertThat(priorAuthorityState(assigned(), legacy).getCaseworkerId()).isNull();
  }

  @Test
  void givenLegacyDocumentTypeUpdated_whenReplayed_thenFoldsLikeCurrentShape() {
    PriorAuthorityDocumentTypeUpdatedEvent current =
        new PriorAuthorityDocumentTypeUpdatedEvent(
            ITEM_ID, DOCUMENT_ID, "INVOICE", OCCURRED_AT, PARENT_ID);
    assertIsLegacyShapeOf(LEGACY_DOCUMENT_TYPE_UPDATED, current, "parentApplicationId");

    PriorAuthorityDocumentTypeUpdatedEvent legacy =
        read(LEGACY_DOCUMENT_TYPE_UPDATED, PriorAuthorityDocumentTypeUpdatedEvent.class);

    assertThat(legacy.parentApplicationId()).isNull();
    assertThat(legacy.genesisIds()).isEmpty();
    assertThat(legacy)
        .usingRecursiveComparison()
        .ignoringFields("parentApplicationId")
        .isEqualTo(current);
    PriorAuthorityState replayed = stateWithUploadedDocument();
    PriorAuthorityEvolve.apply(replayed, legacy);
    PriorAuthorityState expected = stateWithUploadedDocument();
    PriorAuthorityEvolve.apply(expected, current);
    assertThat(replayed).usingRecursiveComparison().isEqualTo(expected);
    assertThat(replayed.getUploadedDocuments().getFirst().documentType()).isEqualTo("INVOICE");
  }

  private static <T> T read(String json, Class<T> type) {
    return converter.convert(json.getBytes(StandardCharsets.UTF_8), type);
  }

  /** Proves the fixture is what the converter wrote before the added fields existed. */
  private static void assertIsLegacyShapeOf(
      String legacyJson, Object current, String... newFields) {
    ObjectNode written =
        (ObjectNode) objectMapper.readTree(converter.convert(current, byte[].class));
    for (String field : newFields) {
      written.remove(field);
    }
    JsonNode legacy = objectMapper.readTree(legacyJson);
    assertThat(written).isEqualTo(legacy);
  }

  private static WorkItemAssigned assigned() {
    return new WorkItemAssigned(
        ITEM_ID, WorkItemType.PRIOR_AUTHORITY, PARENT_ID, 0L, 1L, CASEWORKER_ID, OCCURRED_AT);
  }

  private static ApplicationState applicationState(WorkItemAssigned event) {
    ApplicationState state = new ApplicationState();
    ApplicationEvolve.apply(state, event);
    return state;
  }

  private static ApplicationState applicationState(
      WorkItemAssigned assigned, WorkItemUnassigned unassigned) {
    ApplicationState state = applicationState(assigned);
    ApplicationEvolve.apply(state, unassigned);
    return state;
  }

  private static PriorAuthorityState priorAuthorityState(WorkItemAssigned event) {
    PriorAuthorityState state = new PriorAuthorityState();
    PriorAuthorityEvolve.apply(state, event);
    return state;
  }

  private static PriorAuthorityState priorAuthorityState(
      WorkItemAssigned assigned, WorkItemUnassigned unassigned) {
    PriorAuthorityState state = priorAuthorityState(assigned);
    PriorAuthorityEvolve.apply(state, unassigned);
    return state;
  }

  private static PriorAuthorityState stateWithUploadedDocument() {
    PriorAuthorityState state = new PriorAuthorityState();
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDocumentUploadedEvent(
            ITEM_ID,
            DOCUMENT_ID,
            OCCURRED_AT,
            12L,
            "application/pdf",
            "checksum",
            PARENT_ID,
            "CIVIL_APPLY"));
    return state;
  }
}
