package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

@DisplayName("PriorAuthorityAggregate")
class PriorAuthorityAggregateTest {

  private static final UUID PA_ID = UUID.randomUUID();
  private static final UUID APP_ID = UUID.randomUUID();
  private static final String PA_TYPE = "testType";
  private static final int SCHEMA_VERSION = 1;
  private static final UUID CASEWORKER_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.now();

  @Test
  @DisplayName(
      "on PriorAuthorityDraftStartedEvent delegates to PriorAuthorityEvolve and sets priorAuthorityId")
  void testOnDraftStartedEvent() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    PriorAuthorityDraftStartedEvent event =
        new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW);

    aggregate.on(event);

    assertThat(aggregate.getPriorAuthorityId()).isEqualTo(PA_ID);
    assertThat(aggregate.getApplicationId()).isEqualTo(APP_ID);
    assertThat(aggregate.getPriorAuthorityType()).isEqualTo(PA_TYPE);
  }

  @Test
  @DisplayName(
      "on PriorAuthoritySubmittedEvent delegates to PriorAuthorityEvolve and sets priorAuthorityId")
  void testOnSubmittedEvent() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    PriorAuthoritySubmittedEvent event =
        new PriorAuthoritySubmittedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, 1L, null, NOW);

    aggregate.on(event);

    assertThat(aggregate.getPriorAuthorityId()).isEqualTo(PA_ID);
    assertThat(aggregate.getDataVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "on PriorAuthorityDecisionMadeEvent delegates to PriorAuthorityEvolve and sets priorAuthorityId")
  void testOnDecisionMadeEvent() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    PriorAuthorityDecisionMadeEvent event =
        new PriorAuthorityDecisionMadeEvent(
            PA_ID, APP_ID, PA_TYPE, 2L, "APPROVED", "test justification", BigDecimal.TEN, NOW, NOW);

    aggregate.on(event);

    assertThat(aggregate.getPriorAuthorityId()).isEqualTo(PA_ID);
    assertThat(aggregate.getDataVersion()).isEqualTo(2L);
  }

  @Test
  @DisplayName("on PriorAuthorityDocumentUploadedEvent delegates to PriorAuthorityEvolve")
  void testOnDocumentUploadedEvent() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW));

    UUID docId = UUID.randomUUID();
    PriorAuthorityDocumentUploadedEvent event =
        new PriorAuthorityDocumentUploadedEvent(
            PA_ID, docId, NOW, 1024L, "application/pdf", "checksum123", APP_ID);

    aggregate.on(event);

    assertThat(aggregate.getState().uploadedDocumentIds).contains(docId);
  }

  @Test
  @DisplayName("on PriorAuthorityDocumentDeletedEvent delegates to PriorAuthorityEvolve")
  void testOnDocumentDeletedEvent() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW));
    UUID docId = UUID.randomUUID();
    aggregate.on(
        new PriorAuthorityDocumentUploadedEvent(
            PA_ID, docId, NOW, 1024L, "application/pdf", "checksum123", APP_ID));

    aggregate.on(new PriorAuthorityDocumentDeletedEvent(PA_ID, docId, NOW, APP_ID));

    assertThat(aggregate.getState().uploadedDocumentIds).doesNotContain(docId);
  }

  @Test
  @DisplayName("on PriorAuthorityDocumentTypeUpdatedEvent sets priorAuthorityId from event")
  void testOnDocumentTypeUpdatedEvent() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID docId = UUID.randomUUID();
    PriorAuthorityDocumentTypeUpdatedEvent event =
        new PriorAuthorityDocumentTypeUpdatedEvent(PA_ID, docId, "new-type", NOW);

    aggregate.on(event);

    assertThat(aggregate.getPriorAuthorityId()).isEqualTo(PA_ID);
  }

  @Test
  @DisplayName("on WorkItemAssigned delegates to PriorAuthorityEvolve")
  void testOnWorkItemAssigned() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID workItemId = UUID.randomUUID();
    WorkItemAssigned event =
        new WorkItemAssigned(workItemId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, CASEWORKER_ID, NOW);

    aggregate.on(event);

    assertThat(aggregate.getCaseworkerId()).isEqualTo(CASEWORKER_ID);
    assertThat(aggregate.getAssignmentVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("on WorkItemUnassigned delegates to PriorAuthorityEvolve")
  void testOnWorkItemUnassigned() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID workItemId = UUID.randomUUID();
    aggregate.on(
        new WorkItemAssigned(workItemId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, CASEWORKER_ID, NOW));

    aggregate.on(new WorkItemUnassigned(workItemId, WorkItemType.PRIOR_AUTHORITY, 1L, 2L, NOW));

    assertThat(aggregate.getCaseworkerId()).isNull();
    assertThat(aggregate.getAssignmentVersion()).isEqualTo(2L);
  }

  @Test
  @DisplayName("getApplicationId returns state applicationId")
  void testGetApplicationId() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW));

    assertThat(aggregate.getApplicationId()).isEqualTo(APP_ID);
  }

  @Test
  @DisplayName("getPriorAuthorityType returns state priorAuthorityType")
  void testGetPriorAuthorityType() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW));

    assertThat(aggregate.getPriorAuthorityType()).isEqualTo(PA_TYPE);
  }

  @Test
  @DisplayName("getPriorAuthorityId returns aggregate priorAuthorityId")
  void testGetPriorAuthorityId() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW));

    assertThat(aggregate.getPriorAuthorityId()).isEqualTo(PA_ID);
  }

  @Test
  @DisplayName("getDataVersion returns state dataVersion")
  void testGetDataVersion() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW));
    aggregate.on(
        new PriorAuthoritySubmittedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, 1L, null, NOW));

    assertThat(aggregate.getDataVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("getAssignmentVersion returns state assignmentVersion")
  void testGetAssignmentVersion() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID workItemId = UUID.randomUUID();
    aggregate.on(
        new WorkItemAssigned(workItemId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, CASEWORKER_ID, NOW));

    assertThat(aggregate.getAssignmentVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("getCaseworkerId returns state caseworkerId")
  void testGetCaseworkerId() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID workItemId = UUID.randomUUID();
    aggregate.on(
        new WorkItemAssigned(workItemId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, CASEWORKER_ID, NOW));

    assertThat(aggregate.getCaseworkerId()).isEqualTo(CASEWORKER_ID);
  }

  @Test
  @DisplayName("getState returns internal PriorAuthorityState instance")
  void testGetState() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW));

    PriorAuthorityState state = aggregate.getState();

    assertThat(state.priorAuthorityId).isEqualTo(PA_ID);
    assertThat(state.applicationId).isEqualTo(APP_ID);
    assertThat(state.priorAuthorityType).isEqualTo(PA_TYPE);
  }
}
