package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ChangeLinkedGroupLeadCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupEvolve;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupState;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityData;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataId;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataRepository;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.config.interceptor.RequestMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.observability.ErrorReporter;

@ExtendWith(MockitoExtension.class)
class ApplicationHistoryMapperTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T08:00:00Z");
  private static final UUID APPLICATION = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final UUID GROUP = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID LEAD = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final UUID PRIOR_AUTHORITY =
      UUID.fromString("00000000-0000-0000-0000-00000000000c");
  private static final UUID CASEWORKER = UUID.nameUUIDFromBytes("entra-object-id".getBytes());
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private PriorAuthorityDataRepository priorAuthorityDataRepository;
  @Mock private ErrorReporter errorReporter;

  private ApplicationHistoryMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ApplicationHistoryMapper(priorAuthorityDataRepository, errorReporter);
  }

  @Test
  void givenApplicationCreated_whenMapped_thenGivesRowWithDataVersionAndMetadata() {
    var event =
        new ApplicationCreatedEvent(
            APPLICATION, 1L, "fingerprint", "APPLICATION_IN_PROGRESS", 1, OCCURRED_AT, null, null);

    var rows = mapper.map(APPLICATION, List.of(message(event, "created")));

    assertThat(rows.priorAuthority()).isEmpty();
    assertThat(rows.application())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getEventId()).isEqualTo("created");
              assertThat(row.getApplicationId()).isEqualTo(APPLICATION);
              assertThat(row.getEventType()).isEqualTo("APPLICATION_CREATED");
              assertThat(row.getDataVersion()).isEqualTo(1L);
              assertThat(row.getJustificationId()).isNull();
              assertThat(row.getServiceName()).isEqualTo("CIVIL_APPLY");
              assertThat(row.getCaseworkerId()).isEqualTo(CASEWORKER);
              assertThat(row.getOccurredAt()).isEqualTo(OCCURRED_AT);
            });
  }

  @Test
  void givenApplicationEvents_whenMapped_thenGivesTypedRowsWithDataVersions() {
    var events =
        List.of(
            message(
                new ApplicationUpdatedEvent(APPLICATION, 2L, 2L, "A", "B", OCCURRED_AT), "updated"),
            message(
                new ApplicationDecisionMadeEvent(
                    APPLICATION, 3L, 3L, "GRANTED", AutoGrantedState.MANUAL, OCCURRED_AT),
                "granted"),
            message(
                new ApplicationDecisionMadeEvent(
                    APPLICATION, 4L, 4L, "REFUSED", AutoGrantedState.MANUAL, OCCURRED_AT),
                "refused"),
            message(new NoteCreatedEvent(APPLICATION, 5L, OCCURRED_AT), "note"));

    var rows = mapper.map(APPLICATION, events);

    assertThat(rows.application())
        .extracting(
            ApplicationHistoryRow::getEventId,
            ApplicationHistoryRow::getEventType,
            ApplicationHistoryRow::getDataVersion)
        .containsExactly(
            Tuple.tuple("updated", "APPLICATION_UPDATED", 2L),
            Tuple.tuple("granted", "APPLICATION_MAKE_DECISION_GRANTED", 3L),
            Tuple.tuple("refused", "APPLICATION_MAKE_DECISION_REFUSED", 4L),
            Tuple.tuple("note", "APPLICATION_NOTE_CREATED", 5L));
  }

  @Test
  void givenApplicationEventsForAnotherApplication_whenMapped_thenGivesNoRows() {
    var events =
        List.of(
            message(
                new ApplicationCreatedEvent(
                    OTHER,
                    1L,
                    "fingerprint",
                    "APPLICATION_IN_PROGRESS",
                    1,
                    OCCURRED_AT,
                    null,
                    null),
                "created"),
            message(new ApplicationUpdatedEvent(OTHER, 2L, 2L, "A", "B", OCCURRED_AT), "updated"),
            message(
                new ApplicationDecisionMadeEvent(
                    OTHER, 3L, 3L, "GRANTED", AutoGrantedState.MANUAL, OCCURRED_AT),
                "decision"),
            message(new NoteCreatedEvent(OTHER, 4L, OCCURRED_AT), "note"),
            message(
                new PriorAuthoritySubmittedEvent(
                    PRIOR_AUTHORITY, OTHER, "EXPERT", 1, 2L, 0L, OCCURRED_AT, null),
                "pa-submitted"),
            message(
                new PriorAuthorityDecisionMadeEvent(
                    PRIOR_AUTHORITY, OTHER, "EXPERT", 3L, "GRANTED", null, null, null, OCCURRED_AT),
                "pa-decision"));

    var rows = mapper.map(APPLICATION, events);

    assertThat(rows.application()).isEmpty();
    assertThat(rows.priorAuthority()).isEmpty();
  }

  @Test
  void givenGroupCreated_whenMappedForEachApplication_thenLeadCreatesAndMemberJoins() {
    var justificationId = UUID.randomUUID();
    var event =
        new LinkedApplicationGroupCreatedEvent(
            GROUP, LEAD, List.of(LEAD, MEMBER), OCCURRED_AT, justificationId);
    var events = List.of(message(event, "group-created"));

    assertThat(mapper.map(LEAD, events).application())
        .extracting(
            ApplicationHistoryRow::getEventType,
            ApplicationHistoryRow::getJustificationId,
            ApplicationHistoryRow::getDataVersion)
        .containsExactly(Tuple.tuple("APPLICATION_GROUP_CREATED", justificationId, null));
    assertThat(mapper.map(MEMBER, events).application())
        .extracting(ApplicationHistoryRow::getEventType, ApplicationHistoryRow::getJustificationId)
        .containsExactly(Tuple.tuple("APPLICATION_GROUP_JOINED", justificationId));
    assertThat(mapper.map(THIRD, events).application()).isEmpty();
  }

  @Test
  void givenMemberAdded_whenMappedForEachApplication_thenNewMemberJoinsAndOthersSeeItAdded() {
    var justificationId = UUID.randomUUID();
    var event =
        new MemberAddedToGroupEvent(
            GROUP, LEAD, THIRD, OCCURRED_AT, List.of(LEAD, MEMBER, THIRD), justificationId);
    var events = List.of(message(event, "member-added"));

    assertThat(mapper.map(THIRD, events).application())
        .extracting(ApplicationHistoryRow::getEventType, ApplicationHistoryRow::getJustificationId)
        .containsExactly(Tuple.tuple("APPLICATION_GROUP_JOINED", justificationId));
    assertThat(mapper.map(MEMBER, events).application())
        .extracting(ApplicationHistoryRow::getEventType, ApplicationHistoryRow::getJustificationId)
        .containsExactly(Tuple.tuple("APPLICATION_GROUP_MEMBER_ADDED", justificationId));
    assertThat(mapper.map(OTHER, events).application()).isEmpty();
  }

  @Test
  void givenLegacyMemberAddedWithoutMembers_whenMappedForNonMember_thenGivesNoRow() {
    var events =
        List.of(message(new MemberAddedToGroupEvent(GROUP, LEAD, THIRD, OCCURRED_AT), "added"));

    assertThat(mapper.map(LEAD, events).application()).isEmpty();
    assertThat(mapper.map(THIRD, events).application())
        .extracting(ApplicationHistoryRow::getEventType)
        .containsExactly("APPLICATION_GROUP_JOINED");
  }

  @Test
  void givenLegacyLeadChangedWithoutMembers_whenMapped_thenOnlyBothLeadsSeeIt() {
    var events =
        List.of(
            message(
                new LinkedApplicationGroupLeadChangedEvent(GROUP, LEAD, MEMBER, 1L, OCCURRED_AT),
                "lead-changed"));

    assertThat(mapper.map(LEAD, events).application())
        .extracting(ApplicationHistoryRow::getEventType)
        .containsExactly("APPLICATION_GROUP_LEAD_CHANGED");
    assertThat(mapper.map(MEMBER, events).application())
        .extracting(ApplicationHistoryRow::getEventType)
        .containsExactly("APPLICATION_GROUP_LEAD_CHANGED");
    assertThat(mapper.map(THIRD, events).application()).isEmpty();
  }

  @Test
  void givenLeadChangedWithMembers_whenMapped_thenEveryMemberSeesIt() {
    var justificationId = UUID.randomUUID();
    var events =
        List.of(
            message(
                new LinkedApplicationGroupLeadChangedEvent(
                    GROUP,
                    LEAD,
                    MEMBER,
                    2L,
                    OCCURRED_AT,
                    List.of(LEAD, MEMBER, THIRD),
                    justificationId),
                "lead-changed"));

    assertThat(mapper.map(THIRD, events).application())
        .extracting(ApplicationHistoryRow::getEventType, ApplicationHistoryRow::getJustificationId)
        .containsExactly(Tuple.tuple("APPLICATION_GROUP_LEAD_CHANGED", justificationId));
    assertThat(mapper.map(OTHER, events).application()).isEmpty();
  }

  @Test
  void givenMemberRemoved_whenMappedForEachApplication_thenRemovedLeavesAndOthersSeeItRemoved() {
    var justificationId = UUID.randomUUID();
    var events =
        List.of(
            message(
                new MemberRemovedFromGroupEvent(
                    GROUP,
                    LEAD,
                    THIRD,
                    3L,
                    OCCURRED_AT,
                    List.of(LEAD, MEMBER, THIRD),
                    justificationId),
                "member-removed"));

    assertThat(mapper.map(THIRD, events).application())
        .extracting(ApplicationHistoryRow::getEventType, ApplicationHistoryRow::getJustificationId)
        .containsExactly(Tuple.tuple("APPLICATION_GROUP_LEFT", justificationId));
    assertThat(mapper.map(LEAD, events).application())
        .extracting(ApplicationHistoryRow::getEventType, ApplicationHistoryRow::getJustificationId)
        .containsExactly(Tuple.tuple("APPLICATION_GROUP_MEMBER_REMOVED", justificationId));
    assertThat(mapper.map(OTHER, events).application()).isEmpty();
  }

  @Test
  void givenLegacyMemberRemovedWithoutMembers_whenMappedForNonRemoved_thenGivesNoRow() {
    var events =
        List.of(
            message(
                new MemberRemovedFromGroupEvent(GROUP, LEAD, THIRD, 3L, OCCURRED_AT), "removed"));

    assertThat(mapper.map(LEAD, events).application()).isEmpty();
    assertThat(mapper.map(THIRD, events).application())
        .extracting(ApplicationHistoryRow::getEventType)
        .containsExactly("APPLICATION_GROUP_LEFT");
  }

  @Test
  void givenDissolved_whenMappedForEachApplication_thenRemovedLeavesAndMembersSeeItDissolved() {
    var justificationId = UUID.randomUUID();
    var events =
        List.of(
            message(
                new LinkedApplicationGroupDissolvedEvent(
                    GROUP, LEAD, THIRD, List.of(THIRD, LEAD), 4L, OCCURRED_AT, justificationId),
                "dissolved"));

    assertThat(mapper.map(THIRD, events).application())
        .extracting(ApplicationHistoryRow::getEventType, ApplicationHistoryRow::getJustificationId)
        .containsExactly(Tuple.tuple("APPLICATION_GROUP_LEFT", justificationId));
    assertThat(mapper.map(LEAD, events).application())
        .extracting(ApplicationHistoryRow::getEventType, ApplicationHistoryRow::getJustificationId)
        .containsExactly(Tuple.tuple("APPLICATION_GROUP_DISSOLVED", justificationId));
    assertThat(mapper.map(MEMBER, events).application()).isEmpty();
  }

  @Test
  void givenApplicationWorkItemEvents_whenMapped_thenGivesRowsWithMetadataCaseworker() {
    var events =
        List.of(
            message(
                new WorkItemAssigned(
                    APPLICATION,
                    WorkItemType.APPLICATION,
                    APPLICATION,
                    1L,
                    1L,
                    UUID.randomUUID(),
                    OCCURRED_AT),
                "assigned"),
            message(
                new WorkItemUnassigned(
                    APPLICATION, WorkItemType.APPLICATION, APPLICATION, 1L, 2L, OCCURRED_AT),
                "unassigned"));

    var rows = mapper.map(APPLICATION, events);

    assertThat(rows.application())
        .extracting(
            ApplicationHistoryRow::getEventType,
            ApplicationHistoryRow::getCaseworkerId,
            ApplicationHistoryRow::getDataVersion)
        .containsExactly(
            Tuple.tuple("ASSIGN_APPLICATION_TO_CASEWORKER", CASEWORKER, null),
            Tuple.tuple("UNASSIGN_APPLICATION_TO_CASEWORKER", CASEWORKER, null));
    assertThat(rows.priorAuthority()).isEmpty();
    verifyNoInteractions(priorAuthorityDataRepository);
  }

  @Test
  void givenApplicationWorkItemForAnotherApplication_whenMapped_thenGivesNoRow() {
    var events =
        List.of(
            message(
                new WorkItemAssigned(
                    OTHER, WorkItemType.APPLICATION, OTHER, 1L, 1L, CASEWORKER, OCCURRED_AT),
                "assigned"));

    var rows = mapper.map(APPLICATION, events);

    assertThat(rows.application()).isEmpty();
    assertThat(rows.priorAuthority()).isEmpty();
  }

  @Test
  void givenPriorAuthorityWorkItemEvents_whenMapped_thenGivesPriorAuthorityRows() {
    when(priorAuthorityDataRepository.findById(new PriorAuthorityDataId(PRIOR_AUTHORITY, 2L)))
        .thenReturn(Optional.of(paData(APPLICATION, PriorAuthorityType.EXPERT)));
    var events =
        List.of(
            message(
                new WorkItemAssigned(
                    PRIOR_AUTHORITY,
                    WorkItemType.PRIOR_AUTHORITY,
                    APPLICATION,
                    2L,
                    1L,
                    UUID.randomUUID(),
                    OCCURRED_AT),
                "pa-assigned"),
            message(
                new WorkItemUnassigned(
                    PRIOR_AUTHORITY,
                    WorkItemType.PRIOR_AUTHORITY,
                    APPLICATION,
                    2L,
                    2L,
                    OCCURRED_AT),
                "pa-unassigned"));

    var rows = mapper.map(APPLICATION, events);

    assertThat(rows.application()).isEmpty();
    assertThat(rows.priorAuthority())
        .extracting(
            PriorAuthorityHistoryRow::getEventId,
            PriorAuthorityHistoryRow::getApplicationId,
            PriorAuthorityHistoryRow::getPriorAuthorityId,
            PriorAuthorityHistoryRow::getPriorAuthorityType,
            PriorAuthorityHistoryRow::getEventType,
            PriorAuthorityHistoryRow::getItemVersion,
            PriorAuthorityHistoryRow::getServiceName,
            PriorAuthorityHistoryRow::getCaseworkerId,
            PriorAuthorityHistoryRow::getOccurredAt)
        .containsExactly(
            Tuple.tuple(
                "pa-assigned",
                APPLICATION,
                PRIOR_AUTHORITY,
                "EXPERT",
                "ASSIGN_APPLICATION_TO_CASEWORKER",
                2L,
                "CIVIL_APPLY",
                CASEWORKER,
                OCCURRED_AT),
            Tuple.tuple(
                "pa-unassigned",
                APPLICATION,
                PRIOR_AUTHORITY,
                "EXPERT",
                "UNASSIGN_APPLICATION_TO_CASEWORKER",
                2L,
                "CIVIL_APPLY",
                CASEWORKER,
                OCCURRED_AT));
    verify(errorReporter, never()).report(any(), any());
  }

  @Test
  void givenPriorAuthorityWorkItemWithNoType_whenMapped_thenGivesRowWithNullType() {
    when(priorAuthorityDataRepository.findById(new PriorAuthorityDataId(PRIOR_AUTHORITY, 1L)))
        .thenReturn(Optional.of(paData(APPLICATION, null)));

    var rows = mapper.map(APPLICATION, List.of(message(paAssigned(), "pa-assigned")));

    assertThat(rows.priorAuthority())
        .singleElement()
        .extracting(PriorAuthorityHistoryRow::getPriorAuthorityType)
        .isNull();
  }

  @Test
  void givenPriorAuthorityWorkItemForAnotherApplication_whenMapped_thenGivesNoRow() {
    when(priorAuthorityDataRepository.findById(new PriorAuthorityDataId(PRIOR_AUTHORITY, 1L)))
        .thenReturn(Optional.of(paData(OTHER, PriorAuthorityType.EXPERT)));

    var rows = mapper.map(APPLICATION, List.of(message(paAssigned(), "pa-assigned")));

    assertThat(rows.application()).isEmpty();
    assertThat(rows.priorAuthority()).isEmpty();
    verify(errorReporter, never()).report(any(), any());
  }

  @Test
  void givenPriorAuthorityWorkItemAssignedButNoDataFound_whenMapped_thenReportsAndGivesNoRow() {
    when(priorAuthorityDataRepository.findById(new PriorAuthorityDataId(PRIOR_AUTHORITY, 1L)))
        .thenReturn(Optional.empty());

    var rows = mapper.map(APPLICATION, List.of(message(paAssigned(), "pa-assigned")));

    assertThat(rows.application()).isEmpty();
    assertThat(rows.priorAuthority()).isEmpty();
    verify(errorReporter)
        .report(
            eq("Prior authority data not found for PA assignment event"),
            argThat(
                exception ->
                    exception instanceof ApplicationHistoryIntegrityException integrity
                        && APPLICATION.equals(integrity.getApplicationId())
                        && PRIOR_AUTHORITY.equals(integrity.getPriorAuthorityId())));
  }

  @Test
  void givenPriorAuthorityWorkItemUnassignedButNoDataFound_whenMapped_thenReportsAndGivesNoRow() {
    when(priorAuthorityDataRepository.findById(new PriorAuthorityDataId(PRIOR_AUTHORITY, 1L)))
        .thenReturn(Optional.empty());
    var event =
        new WorkItemUnassigned(
            PRIOR_AUTHORITY, WorkItemType.PRIOR_AUTHORITY, APPLICATION, 1L, 2L, OCCURRED_AT);

    var rows = mapper.map(APPLICATION, List.of(message(event, "pa-unassigned")));

    assertThat(rows.priorAuthority()).isEmpty();
    verify(errorReporter)
        .report(
            eq("Prior authority data not found for PA unassignment event"),
            any(ApplicationHistoryIntegrityException.class));
  }

  @Test
  void givenPriorAuthoritySubmittedAndDecided_whenMapped_thenGivesPriorAuthorityRows() {
    var events =
        List.of(
            message(
                new PriorAuthoritySubmittedEvent(
                    PRIOR_AUTHORITY, APPLICATION, "EXPERT", 1, 2L, 0L, OCCURRED_AT, null),
                "pa-submitted"),
            message(paDecision("GRANTED"), "pa-granted"),
            message(paDecision("REFUSED"), "pa-refused"));

    var rows = mapper.map(APPLICATION, events);

    assertThat(rows.application()).isEmpty();
    assertThat(rows.priorAuthority())
        .extracting(
            PriorAuthorityHistoryRow::getEventId,
            PriorAuthorityHistoryRow::getPriorAuthorityId,
            PriorAuthorityHistoryRow::getPriorAuthorityType,
            PriorAuthorityHistoryRow::getEventType,
            PriorAuthorityHistoryRow::getItemVersion,
            PriorAuthorityHistoryRow::getServiceName,
            PriorAuthorityHistoryRow::getCaseworkerId)
        .containsExactly(
            Tuple.tuple(
                "pa-submitted",
                PRIOR_AUTHORITY,
                "EXPERT",
                "PRIOR_AUTHORITY_SUBMITTED",
                2L,
                "CIVIL_APPLY",
                CASEWORKER),
            Tuple.tuple(
                "pa-granted",
                PRIOR_AUTHORITY,
                "EXPERT",
                "PRIOR_AUTHORITY_MAKE_DECISION_GRANTED",
                3L,
                "CIVIL_APPLY",
                CASEWORKER),
            Tuple.tuple(
                "pa-refused",
                PRIOR_AUTHORITY,
                "EXPERT",
                "PRIOR_AUTHORITY_MAKE_DECISION_REFUSED",
                3L,
                "CIVIL_APPLY",
                CASEWORKER));
  }

  @Test
  void givenPriorAuthorityUnknownDecision_whenMapped_thenThrowsIntegrityException() {
    var events = List.of(message(paDecision("PART_GRANTED"), "pa-unknown"));

    assertThatThrownBy(() -> mapper.map(APPLICATION, events))
        .isInstanceOf(ApplicationHistoryIntegrityException.class)
        .hasMessageContaining("unsupported prior-authority decision value")
        .satisfies(
            exception -> {
              var integrity = (ApplicationHistoryIntegrityException) exception;
              assertThat(integrity.getApplicationId()).isEqualTo(APPLICATION);
              assertThat(integrity.getPriorAuthorityId()).isEqualTo(PRIOR_AUTHORITY);
            });
  }

  @Test
  void givenMessagesWithoutMetadata_whenMapped_thenServiceNameAndCaseworkerAreNull() {
    List<EventMessage> events =
        List.of(
            new GenericEventMessage(
                "note",
                new MessageType(NoteCreatedEvent.class),
                new NoteCreatedEvent(APPLICATION, 1L, OCCURRED_AT),
                Map.of(),
                OCCURRED_AT),
            new GenericEventMessage(
                "pa-submitted",
                new MessageType(PriorAuthoritySubmittedEvent.class),
                new PriorAuthoritySubmittedEvent(
                    PRIOR_AUTHORITY, APPLICATION, "EXPERT", 1, 2L, 0L, OCCURRED_AT, null),
                Map.of(),
                OCCURRED_AT));

    var rows = mapper.map(APPLICATION, events);

    assertThat(rows.application())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getServiceName()).isNull();
              assertThat(row.getCaseworkerId()).isNull();
            });
    assertThat(rows.priorAuthority())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getServiceName()).isNull();
              assertThat(row.getCaseworkerId()).isNull();
            });
  }

  @Test
  void givenEventNotShownInHistory_whenMapped_thenGivesNoRows() {
    var rows = mapper.map(APPLICATION, List.of(message("not an event", "other")));

    assertThat(rows.application()).isEmpty();
    assertThat(rows.priorAuthority()).isEmpty();
  }

  @Test
  void givenNoEvents_whenMapped_thenGivesNoRows() {
    var rows = mapper.map(APPLICATION, List.of());

    assertThat(rows.application()).isEmpty();
    assertThat(rows.priorAuthority()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenEventsInStreamOrder_whenMapped_thenRowsKeepThatOrder(boolean noteFirst) {
    var note = message(new NoteCreatedEvent(APPLICATION, 1L, OCCURRED_AT), "note");
    var update =
        message(
            new ApplicationUpdatedEvent(
                APPLICATION, 2L, 2L, "A", "B", OCCURRED_AT.minusSeconds(60)),
            "update");

    var rows = mapper.map(APPLICATION, noteFirst ? List.of(note, update) : List.of(update, note));

    assertThat(rows.application())
        .extracting(ApplicationHistoryRow::getEventId)
        .containsExactlyElementsOf(
            noteFirst ? List.of("note", "update") : List.of("update", "note"));
  }

  @Test
  void givenMappedRows_whenModified_thenRejected() {
    var rows =
        mapper.map(
            APPLICATION,
            List.of(message(new NoteCreatedEvent(APPLICATION, 1L, OCCURRED_AT), "note")));

    assertThatThrownBy(() -> rows.application().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> rows.priorAuthority().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /**
   * Historical payloads without membership snapshots or justification references must still
   * deserialise, map with their original recipients and restore state for later commands.
   */
  @Test
  void givenHistoricalJsonWithoutNewFields_whenMapped_thenOriginalCoverageAndStateArePreserved() {
    var created =
        MAPPER.readValue(
            """
        {"groupId":"%s","leadApplicationId":"%s","memberApplicationIds":["%s","%s"],
         "occurredAt":"2026-07-15T08:00:00Z"}
        """
                .formatted(GROUP, LEAD, LEAD, MEMBER),
            LinkedApplicationGroupCreatedEvent.class);
    var added =
        MAPPER.readValue(
            """
        {"groupId":"%s","leadApplicationId":"%s","memberId":"%s",
         "occurredAt":"2026-07-15T08:00:00Z"}
        """
                .formatted(GROUP, LEAD, THIRD),
            MemberAddedToGroupEvent.class);
    var lead =
        MAPPER.readValue(
            """
        {"groupId":"%s","previousLeadApplicationId":"%s","newLeadApplicationId":"%s",
         "groupVersion":2,"occurredAt":"2026-07-15T08:00:00Z"}
        """
                .formatted(GROUP, LEAD, MEMBER),
            LinkedApplicationGroupLeadChangedEvent.class);
    var removed =
        MAPPER.readValue(
            """
        {"groupId":"%s","leadApplicationId":"%s","memberId":"%s",
         "groupVersion":3,"occurredAt":"2026-07-15T08:00:00Z"}
        """
                .formatted(GROUP, MEMBER, THIRD),
            MemberRemovedFromGroupEvent.class);
    var dissolved =
        MAPPER.readValue(
            """
        {"groupId":"%s","formerLeadApplicationId":"%s","removedApplicationId":"%s",
         "memberApplicationIds":["%s","%s"],"groupVersion":4,
         "occurredAt":"2026-07-15T08:00:00Z"}
        """
                .formatted(GROUP, MEMBER, LEAD, LEAD, MEMBER),
            LinkedApplicationGroupDissolvedEvent.class);
    List<EventMessage> events =
        List.of(
            message(created, "created"),
            message(added, "added"),
            message(lead, "lead"),
            message(removed, "removed"),
            message(dissolved, "dissolved"));

    assertThat(added.memberApplicationIds()).isNull();
    assertThat(lead.memberApplicationIds()).isNull();
    assertThat(removed.memberApplicationIds()).isNull();
    assertThat(mapper.map(LEAD, events).application())
        .extracting(ApplicationHistoryRow::getEventId, ApplicationHistoryRow::getEventType)
        .containsExactly(
            Tuple.tuple("created", "APPLICATION_GROUP_CREATED"),
            Tuple.tuple("lead", "APPLICATION_GROUP_LEAD_CHANGED"),
            Tuple.tuple("dissolved", "APPLICATION_GROUP_LEFT"));
    assertThat(mapper.map(MEMBER, events).application())
        .extracting(ApplicationHistoryRow::getEventId, ApplicationHistoryRow::getEventType)
        .containsExactly(
            Tuple.tuple("created", "APPLICATION_GROUP_JOINED"),
            Tuple.tuple("lead", "APPLICATION_GROUP_LEAD_CHANGED"),
            Tuple.tuple("dissolved", "APPLICATION_GROUP_DISSOLVED"));
    assertThat(mapper.map(THIRD, events).application())
        .extracting(ApplicationHistoryRow::getEventId, ApplicationHistoryRow::getEventType)
        .containsExactly(
            Tuple.tuple("added", "APPLICATION_GROUP_JOINED"),
            Tuple.tuple("removed", "APPLICATION_GROUP_LEFT"));
    assertThat(
            List.of(LEAD, MEMBER, THIRD).stream()
                .flatMap(id -> mapper.map(id, events).application().stream()))
        .extracting(ApplicationHistoryRow::getJustificationId)
        .containsOnlyNulls();

    var state = new LinkedApplicationGroupState();
    LinkedApplicationGroupEvolve.apply(state, created);
    LinkedApplicationGroupEvolve.apply(state, added);
    LinkedApplicationGroupEvolve.apply(state, lead);
    LinkedApplicationGroupEvolve.apply(state, removed);
    var next =
        LinkedApplicationGroupDecider.decideChangeLead(
                state, new ChangeLinkedGroupLeadCommand(GROUP, LEAD, 3, OCCURRED_AT))
            .orElseThrow();
    assertThat(next.groupVersion()).isEqualTo(4);
    assertThat(next.memberApplicationIds()).containsExactly(LEAD, MEMBER);
    LinkedApplicationGroupEvolve.apply(state, dissolved);
    assertThat(state.isDissolved()).isTrue();
    assertThat(state.memberIdsOrEmpty()).isEmpty();
  }

  private static WorkItemAssigned paAssigned() {
    return new WorkItemAssigned(
        PRIOR_AUTHORITY,
        WorkItemType.PRIOR_AUTHORITY,
        APPLICATION,
        1L,
        1L,
        UUID.randomUUID(),
        OCCURRED_AT);
  }

  private static PriorAuthorityDecisionMadeEvent paDecision(String decision) {
    return new PriorAuthorityDecisionMadeEvent(
        PRIOR_AUTHORITY, APPLICATION, "EXPERT", 3L, decision, "Reasons", null, null, OCCURRED_AT);
  }

  private static PriorAuthorityData paData(UUID applicationId, PriorAuthorityType type) {
    var content = new PriorAuthorityContent(type, null, null, null, null);
    var payload =
        new PriorAuthorityDataPayload(PRIOR_AUTHORITY, applicationId, content, null, OCCURRED_AT);
    return PriorAuthorityData.builder()
        .applicationId(applicationId)
        .payload(payload)
        .payloadHash("hash")
        .createdAt(OCCURRED_AT)
        .build();
  }

  private static EventMessage message(Object payload, String identifier) {
    return new GenericEventMessage(
        identifier,
        new MessageType(payload.getClass()),
        payload,
        Map.of(
            RequestMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY,
            "CIVIL_APPLY",
            RequestMetadataDispatchInterceptor.AUTHENTICATED_USER_ID_KEY,
            CASEWORKER.toString()),
        OCCURRED_AT);
  }
}
