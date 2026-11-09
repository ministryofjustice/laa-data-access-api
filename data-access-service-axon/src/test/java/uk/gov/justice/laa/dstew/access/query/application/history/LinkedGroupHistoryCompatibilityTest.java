package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ChangeLinkedGroupLeadCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupEvolve;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupState;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;

class LinkedGroupHistoryCompatibilityTest {
  private static final UUID GROUP = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID LEAD = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final Instant TIME = Instant.parse("2026-07-15T08:00:00Z");
  private final ObjectMapper mapper = new ObjectMapper();
  private final ApplicationHistoryReadRepository repository =
      mock(ApplicationHistoryReadRepository.class);
  private final ApplicationHistoryProjection projection =
      new ApplicationHistoryProjection(
          repository,
          mock(ApplicationHistoryAssembler.class),
          mock(PriorAuthorityHistoryReadRepository.class),
          mock(PriorAuthorityHistoryAssembler.class));

  @Test
  void givenHistoricalJsonWithoutNewFields_whenReplayed_thenOriginalCoverageAndStateArePreserved() {
    var created =
        mapper.readValue(
            """
        {"groupId":"%s","leadApplicationId":"%s","memberApplicationIds":["%s","%s"],
         "occurredAt":"2026-07-15T08:00:00Z"}
        """
                .formatted(GROUP, LEAD, LEAD, MEMBER),
            LinkedApplicationGroupCreatedEvent.class);
    var added =
        mapper.readValue(
            """
        {"groupId":"%s","leadApplicationId":"%s","memberId":"%s",
         "occurredAt":"2026-07-15T08:00:00Z"}
        """
                .formatted(GROUP, LEAD, THIRD),
            MemberAddedToGroupEvent.class);
    var lead =
        mapper.readValue(
            """
        {"groupId":"%s","previousLeadApplicationId":"%s","newLeadApplicationId":"%s",
         "groupVersion":2,"occurredAt":"2026-07-15T08:00:00Z"}
        """
                .formatted(GROUP, LEAD, MEMBER),
            LinkedApplicationGroupLeadChangedEvent.class);
    var removed =
        mapper.readValue(
            """
        {"groupId":"%s","leadApplicationId":"%s","memberId":"%s",
         "groupVersion":3,"occurredAt":"2026-07-15T08:00:00Z"}
        """
                .formatted(GROUP, MEMBER, THIRD),
            MemberRemovedFromGroupEvent.class);
    var dissolved =
        mapper.readValue(
            """
        {"groupId":"%s","formerLeadApplicationId":"%s","removedApplicationId":"%s",
         "memberApplicationIds":["%s","%s"],"groupVersion":4,
         "occurredAt":"2026-07-15T08:00:00Z"}
        """
                .formatted(GROUP, MEMBER, LEAD, LEAD, MEMBER),
            LinkedApplicationGroupDissolvedEvent.class);

    projection.on(created, message(created, "created"));
    projection.on(added, message(added, "added"));
    projection.on(lead, message(lead, "lead"));
    projection.on(removed, message(removed, "removed"));
    projection.on(dissolved, message(dissolved, "dissolved"));
    var rows = ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository, times(8)).save(rows.capture());
    assertThat(rows.getAllValues())
        .extracting(ApplicationHistoryReadModel::getJustificationId)
        .containsOnlyNulls();
    assertThat(rows.getAllValues())
        .extracting(ApplicationHistoryReadModel::getEventId)
        .containsExactly(
            "created:" + LEAD,
            "created:" + MEMBER,
            "added:" + THIRD,
            "lead:" + MEMBER,
            "lead:" + LEAD,
            "removed:" + THIRD,
            "dissolved:" + LEAD,
            "dissolved:" + MEMBER);
    assertThat(added.memberApplicationIds()).isNull();
    assertThat(lead.memberApplicationIds()).isNull();
    assertThat(removed.memberApplicationIds()).isNull();

    var state = new LinkedApplicationGroupState();
    LinkedApplicationGroupEvolve.apply(state, created);
    LinkedApplicationGroupEvolve.apply(state, added);
    LinkedApplicationGroupEvolve.apply(state, lead);
    LinkedApplicationGroupEvolve.apply(state, removed);
    var next =
        LinkedApplicationGroupDecider.decideChangeLead(
                state, new ChangeLinkedGroupLeadCommand(GROUP, LEAD, 3, TIME))
            .orElseThrow();
    assertThat(next.groupVersion()).isEqualTo(4);
    assertThat(next.memberApplicationIds()).containsExactly(LEAD, MEMBER);
    LinkedApplicationGroupEvolve.apply(state, dissolved);
    assertThat(state.isDissolved()).isTrue();
    assertThat(state.memberIdsOrEmpty()).isEmpty();
  }

  @Test
  void
      givenNewMembershipSnapshots_whenProjected_thenEveryMemberReceivesCorrectActionAndReference() {
    var id = UUID.randomUUID();
    var members = List.of(LEAD, MEMBER, THIRD);
    var added = new MemberAddedToGroupEvent(GROUP, LEAD, THIRD, TIME, members, id);
    var lead =
        new LinkedApplicationGroupLeadChangedEvent(GROUP, LEAD, MEMBER, 2, TIME, members, id);
    var removed = new MemberRemovedFromGroupEvent(GROUP, MEMBER, THIRD, 3, TIME, members, id);
    projection.on(added, message(added, "added"));
    projection.on(lead, message(lead, "lead"));
    projection.on(removed, message(removed, "removed"));
    var rows = ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository, times(9)).save(rows.capture());
    assertThat(rows.getAllValues())
        .extracting(ApplicationHistoryReadModel::getJustificationId)
        .containsOnly(id);
    assertThat(rows.getAllValues())
        .extracting(ApplicationHistoryReadModel::getEventType)
        .containsExactly(
            "APPLICATION_GROUP_JOINED",
            "APPLICATION_GROUP_MEMBER_ADDED",
            "APPLICATION_GROUP_MEMBER_ADDED",
            "APPLICATION_GROUP_LEAD_CHANGED",
            "APPLICATION_GROUP_LEAD_CHANGED",
            "APPLICATION_GROUP_LEAD_CHANGED",
            "APPLICATION_GROUP_LEFT",
            "APPLICATION_GROUP_MEMBER_REMOVED",
            "APPLICATION_GROUP_MEMBER_REMOVED");
    for (var member : members) {
      assertThat(rows.getAllValues().stream().filter(row -> row.getApplicationId().equals(member)))
          .hasSize(3);
    }
  }

  private GenericEventMessage message(Object event, String id) {
    return new GenericEventMessage(id, new MessageType(event.getClass()), event, Map.of(), TIME);
  }
}
