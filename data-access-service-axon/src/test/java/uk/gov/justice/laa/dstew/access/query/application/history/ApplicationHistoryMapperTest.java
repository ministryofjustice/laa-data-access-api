package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.config.interceptor.RequestMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

class ApplicationHistoryMapperTest {

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID CASEWORKER_ID = UUID.randomUUID();
  private static final Instant AT = Instant.parse("2026-11-06T10:00:00Z");
  private static final List<String> ALL =
      List.of("APPLICATION_NOTE_CREATED", "APPLICATION_GROUP_LEFT", "APPLICATION_GROUP_DISSOLVED");

  @Test
  void givenDissolvedGroup_whenMapped_thenRemovedLeavesMembersDissolveAndFormerLeadGetsNothing() {
    UUID removed = UUID.randomUUID();
    UUID member = UUID.randomUUID();
    var dissolved =
        new LinkedApplicationGroupDissolvedEvent(
            UUID.randomUUID(), APPLICATION_ID, removed, List.of(removed, member), 3L, AT);

    assertThat(map(APPLICATION_ID, dissolved).applicationHistoryEvents()).isEmpty();
    assertThat(map(removed, dissolved).applicationHistoryEvents())
        .extracting(ApplicationHistoryEventResult::eventType)
        .containsExactly("APPLICATION_GROUP_LEFT");
    assertThat(map(member, dissolved).applicationHistoryEvents())
        .extracting(ApplicationHistoryEventResult::eventType)
        .containsExactly("APPLICATION_GROUP_DISSOLVED");
  }

  @Test
  void givenNotes_whenMapped_thenTextIsDescriptionAndErasedTextIsAbsent() {
    var history =
        map(
            APPLICATION_ID,
            new NoteCreatedEvent(APPLICATION_ID, AT, Erasable.of("A note")),
            new NoteCreatedEvent(APPLICATION_ID, AT, Erasable.erased()));

    assertThat(history.applicationHistoryEvents())
        .extracting(ApplicationHistoryEventResult::eventDescription)
        .containsExactly("A note", null);
    assertThat(history.applicationHistoryEvents().getFirst().serviceName())
        .isEqualTo("CIVIL_APPLY");
    assertThat(history.applicationHistoryEvents().getFirst().caseworkerId())
        .isEqualTo(CASEWORKER_ID);
  }

  @Test
  void givenUnrequestedType_whenMapped_thenApplicationEntryIsDroppedButPriorAuthorityKept() {
    var history =
        ApplicationHistoryMapper.map(
            APPLICATION_ID,
            List.of(
                message(new NoteCreatedEvent(APPLICATION_ID, AT, Erasable.of("A note"))),
                message(submitted(UUID.randomUUID(), "EXPERT"))),
            List.of("APPLICATION_CREATED"));

    assertThat(history.applicationHistoryEvents()).isEmpty();
    assertThat(history.priorAuthorityHistoryGroups()).hasSize(1);
  }

  @Test
  void givenPriorAuthorityAssignment_whenMapped_thenGroupedUnderSubmittedType() {
    UUID priorAuthorityId = UUID.randomUUID();
    var history =
        map(
            APPLICATION_ID,
            assigned(priorAuthorityId),
            submitted(priorAuthorityId, "EXPERT"),
            assigned(UUID.randomUUID()));

    assertThat(history.priorAuthorityHistoryGroups())
        .singleElement()
        .satisfies(
            group -> {
              assertThat(group.priorAuthorityId()).isEqualTo(priorAuthorityId);
              assertThat(group.priorAuthorityType()).isEqualTo("EXPERT");
              assertThat(group.events())
                  .extracting(PriorAuthorityHistoryEventResult::eventType)
                  .containsExactly("ASSIGN_APPLICATION_TO_CASEWORKER", "PRIOR_AUTHORITY_SUBMITTED");
            });
  }

  @Test
  void givenPriorAuthorityDecision_whenMapped_thenDescriptionCarriesOutcomeAndJustification() {
    var history = map(APPLICATION_ID, decision(UUID.randomUUID(), "EXPERT", "GRANTED"));

    assertThat(history.priorAuthorityHistoryGroups().getFirst().events())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.eventType()).isEqualTo("PRIOR_AUTHORITY_MAKE_DECISION_GRANTED");
              assertThat(event.eventDescription())
                  .isEqualTo("Outcome: Granted, Decision at: " + AT + ", Justification: Needed");
            });
  }

  @Test
  void givenConflictingPriorAuthorityTypes_whenMapped_thenIntegrityFails() {
    UUID priorAuthorityId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                map(
                    APPLICATION_ID,
                    submitted(priorAuthorityId, "EXPERT"),
                    decision(priorAuthorityId, "COUNSEL", "GRANTED")))
        .isInstanceOf(ApplicationHistoryIntegrityException.class);
  }

  @Test
  void givenUnsupportedPriorAuthorityDecision_whenMapped_thenIntegrityFails() {
    assertThatThrownBy(
            () -> map(APPLICATION_ID, decision(UUID.randomUUID(), "EXPERT", "PARTIALLY_GRANTED")))
        .isInstanceOf(ApplicationHistoryIntegrityException.class);
  }

  private static ApplicationHistoryResult map(UUID applicationId, Object... payloads) {
    return ApplicationHistoryMapper.map(
        applicationId,
        List.of(payloads).stream().map(ApplicationHistoryMapperTest::message).toList(),
        ALL);
  }

  private static EventMessage message(Object payload) {
    return new GenericEventMessage(
        UUID.randomUUID().toString(),
        new MessageType(payload.getClass()),
        payload,
        Map.of(
            RequestMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY,
            "CIVIL_APPLY",
            RequestMetadataDispatchInterceptor.AUTHENTICATED_USER_ID_KEY,
            CASEWORKER_ID.toString()),
        AT);
  }

  private static PriorAuthoritySubmittedEvent submitted(UUID priorAuthorityId, String type) {
    return new PriorAuthoritySubmittedEvent(priorAuthorityId, APPLICATION_ID, type, 1, null, AT);
  }

  private static PriorAuthorityDecisionMadeEvent decision(
      UUID priorAuthorityId, String type, String outcome) {
    return new PriorAuthorityDecisionMadeEvent(
        priorAuthorityId,
        APPLICATION_ID,
        type,
        outcome,
        Erasable.of("Needed"),
        Erasable.erased(),
        Erasable.erased(),
        Erasable.erased(),
        Erasable.erased(),
        Erasable.erased(),
        AT);
  }

  private static WorkItemAssigned assigned(UUID workItemId) {
    return new WorkItemAssigned(
        workItemId, WorkItemType.PRIOR_AUTHORITY, APPLICATION_ID, 1L, 1L, CASEWORKER_ID, AT);
  }
}
