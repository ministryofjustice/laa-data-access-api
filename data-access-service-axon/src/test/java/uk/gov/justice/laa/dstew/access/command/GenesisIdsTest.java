package uk.gov.justice.laa.dstew.access.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/** Each event names the root applications it relates to, derived only from its own fields. */
public class GenesisIdsTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-11-06T10:00:00Z");
  private static final UUID APP = UUID.randomUUID();
  private static final UUID LEAD = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();
  private static final UUID ID = UUID.randomUUID();

  /** One instance of every event, with the genesis IDs it should name. */
  public static Stream<Arguments> events() {
    return Stream.of(
        scoped(
            new ApplicationCreatedEvent(APP, 1L, "fp", "S", 1, OCCURRED_AT, List.of(), "OFF"), APP),
        scoped(new ApplicationDraftStartedEvent(APP, 1, "fp", OCCURRED_AT, "OFF"), APP),
        scoped(new ApplicationUpdatedEvent(APP, 1L, 1L, "A", "B", OCCURRED_AT), APP),
        scoped(new ApplicationReadyForManualAssessmentEvent(APP, 1L, 1L, OCCURRED_AT), APP),
        scoped(
            new ApplicationDecisionMadeEvent(
                APP, 1L, 1L, "GRANTED", AutoGrantedState.MANUAL, OCCURRED_AT),
            APP),
        scoped(
            new ApplicationDocumentUploadedEvent(APP, ID, "T", OCCURRED_AT, 1L, "ct", "cs", "svc"),
            APP),
        scoped(new NoteCreatedEvent(APP, 1L, OCCURRED_AT), APP),
        scoped(new PriorAuthorityDraftStartedEvent(ID, APP, "T", 1, OCCURRED_AT), APP),
        scoped(new PriorAuthorityDraftUpdatedEvent(ID, APP, OCCURRED_AT), APP),
        scoped(new PriorAuthoritySubmittedEvent(ID, APP, "T", 1, 1L, null, OCCURRED_AT, "B"), APP),
        scoped(
            new PriorAuthorityDecisionMadeEvent(
                ID, APP, "T", 1L, "GRANTED", "why", BigDecimal.ONE, OCCURRED_AT, OCCURRED_AT),
            APP),
        scoped(
            new PriorAuthorityDocumentUploadedEvent(
                ID, ID, OCCURRED_AT, 1L, "ct", "cs", APP, "svc"),
            APP),
        scoped(new PriorAuthorityDocumentDeletedEvent(ID, ID, OCCURRED_AT, APP), APP),
        scoped(new PriorAuthorityDocumentTypeUpdatedEvent(ID, ID, "T", OCCURRED_AT, APP), APP),
        scoped(
            new WorkItemAssigned(ID, WorkItemType.PRIOR_AUTHORITY, APP, 1L, 1L, ID, OCCURRED_AT),
            APP),
        scoped(new WorkItemUnassigned(ID, WorkItemType.APPLICATION, APP, 1L, 1L, OCCURRED_AT), APP),
        scoped(
            new LinkedApplicationGroupCreatedEvent(ID, LEAD, List.of(LEAD, MEMBER), OCCURRED_AT),
            LEAD,
            MEMBER),
        scoped(new MemberAddedToGroupEvent(ID, LEAD, MEMBER, OCCURRED_AT), LEAD, MEMBER),
        scoped(
            new MemberAddedToGroupEvent(
                ID, LEAD, MEMBER, OCCURRED_AT, List.of(LEAD, OTHER, MEMBER), null),
            LEAD,
            MEMBER,
            OTHER),
        scoped(new MemberRemovedFromGroupEvent(ID, LEAD, MEMBER, 2L, OCCURRED_AT), LEAD, MEMBER),
        scoped(
            new MemberRemovedFromGroupEvent(
                ID, LEAD, MEMBER, 2L, OCCURRED_AT, List.of(LEAD, OTHER), null),
            LEAD,
            MEMBER,
            OTHER),
        scoped(
            new LinkedApplicationGroupLeadChangedEvent(ID, LEAD, OTHER, 2L, OCCURRED_AT),
            LEAD,
            OTHER),
        scoped(
            new LinkedApplicationGroupLeadChangedEvent(
                ID, LEAD, OTHER, 2L, OCCURRED_AT, List.of(LEAD, OTHER, MEMBER), null),
            LEAD,
            OTHER,
            MEMBER),
        scoped(
            new LinkedApplicationGroupDissolvedEvent(
                ID, LEAD, MEMBER, List.of(LEAD), 2L, OCCURRED_AT),
            LEAD,
            MEMBER));
  }

  /** Legacy stored shapes that load without the application they concern. */
  public static Stream<Arguments> legacyEvents() {
    return Stream.of(
        legacy(new WorkItemAssigned(ID, WorkItemType.APPLICATION, null, 1L, 1L, ID, OCCURRED_AT)),
        legacy(new WorkItemUnassigned(ID, WorkItemType.APPLICATION, null, 1L, 1L, OCCURRED_AT)),
        legacy(new PriorAuthorityDocumentTypeUpdatedEvent(ID, ID, "T", OCCURRED_AT, null)),
        legacy(new MemberAddedToGroupEvent(ID, null, MEMBER, OCCURRED_AT), MEMBER));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource({"events", "legacyEvents"})
  void givenEvent_whenScoped_thenNamesItsRootApplications(
      String name, GenesisScoped event, List<UUID> expected) {
    assertThat(event.genesisIds()).containsExactlyInAnyOrderElementsOf(expected);
  }

  private static Arguments legacy(GenesisScoped event, UUID... expected) {
    return Arguments.of(
        "legacy " + event.getClass().getSimpleName(), event, Arrays.asList(expected));
  }

  private static Arguments scoped(GenesisScoped event, UUID... expected) {
    return Arguments.of(event.getClass().getSimpleName(), event, Arrays.asList(expected));
  }
}
