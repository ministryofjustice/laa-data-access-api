package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/** Verifies linked-group assignment and unassignment through the HTTP API and event store. */
class LinkedGroupWorkItemAssignmentIntegrationTest extends AbstractLinkedGroupIntegrationTest {

  @Test
  void givenAnUnassignedLinkedGroup_whenOneMemberIsAssigned_thenEveryMemberIsAssigned() {
    var memberIds = createGroup(3);

    assertThat(assign(memberIds.get(1), TestJwtDecoderConfig.CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    var groupId = routeRepository.findById(memberIds.getFirst()).orElseThrow().getGroupId();
    for (var memberId : memberIds) {
      awaitWorkListAssignee(memberId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
      awaitWorkListBoundary(memberId, "LINKED_GROUP", groupId);
    }
  }

  @Test
  void givenAssignedGroup_whenOtherCaseworkerAssigns_thenConflictUntilUnassigned() {
    var memberIds = createGroup(3);
    assertThat(assign(memberIds.getFirst(), TestJwtDecoderConfig.CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    memberIds.forEach(
        memberId -> awaitWorkListAssignee(memberId, TestJwtDecoderConfig.CASEWORKER_ID, 1L));

    var reassignment = assign(memberIds.get(2), TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 1L);
    assertThat(reassignment.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(reassignment.getBody()).contains("it is already assigned");

    assertThat(
            unassign(memberIds.get(2), TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 1L)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    memberIds.forEach(memberId -> awaitWorkListAssignee(memberId, null, 2L));

    assertThat(
            assign(memberIds.getFirst(), TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 2L)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    memberIds.forEach(
        memberId -> awaitWorkListAssignee(memberId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 3L));
  }

  @Test
  void givenStaleVersion_whenAssign_thenConflictAndNoEvents() {
    var memberIds = createGroup(2);
    var targetId = memberIds.getFirst();
    assertThat(assign(targetId, TestJwtDecoderConfig.CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    var staleReassignment = assign(targetId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 0L);

    assertThat(staleReassignment.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    memberIds.forEach(
        memberId -> assertThat(countDomainEvents(memberId, WorkItemAssigned.class)).isEqualTo(1));
  }

  @Test
  void givenGroupDispatchFailsPartway_whenAssigning_thenNoMemberIsAssigned() {
    var memberIds = createGroup(3);
    var poisonMemberId = memberIds.stream().max(Comparator.naturalOrder()).orElseThrow();
    rejectEventsFor(poisonMemberId);

    ResponseEntity<String> response =
        assign(memberIds.getFirst(), TestJwtDecoderConfig.CASEWORKER_ID, 0L);

    assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    memberIds.forEach(
        memberId -> assertThat(countDomainEvents(memberId, WorkItemAssigned.class)).isZero());
  }

  @Test
  void givenNotReadyMember_whenGroupAssigned_thenOnlyReadyMembersAssigned() {
    var lastName = uniqueLastName();
    var leadId = createApplication(lastName);
    var readyMemberId = createApplication(lastName);
    var notReadyMemberId = createSubmittedApplication(lastName);
    assertThat(link(readyMemberId, leadId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var groupId =
        Objects.requireNonNull(awaitRoute(leadId, ApplicationGroupRouteKind.LINKED_GROUP));
    assertThat(link(notReadyMemberId, leadId, groupToken(groupId, 0)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(assign(leadId, TestJwtDecoderConfig.CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    awaitWorkListAssignee(leadId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
    awaitWorkListAssignee(readyMemberId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
    assertThat(countDomainEvents(notReadyMemberId, WorkItemAssigned.class)).isZero();
  }

  @Test
  void givenLinkedBeforeReady_whenMarkedReady_thenWorkItemUsesGroupBoundary() {
    var lastName = uniqueLastName();
    var leadId = createApplication(lastName);
    var memberId = createSubmittedApplication(lastName);
    assertThat(link(memberId, leadId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var groupId =
        Objects.requireNonNull(awaitRoute(leadId, ApplicationGroupRouteKind.LINKED_GROUP));
    awaitWorkListBoundary(leadId, "LINKED_GROUP", groupId);

    assertThat(markReady(memberId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    awaitWorkListBoundary(memberId, "LINKED_GROUP", groupId);
  }

  @Test
  void givenThreeMemberGroup_whenMemberUnlinked_thenOnlyThatMemberUsesDirectBoundary() {
    var memberIds = createGroup(3);
    var groupId = routeRepository.findById(memberIds.getFirst()).orElseThrow().getGroupId();
    var removedId = memberIds.get(1);

    assertThat(unlink(removedId, groupToken(groupId, 1L)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    awaitWorkListBoundary(removedId, "DIRECT", removedId);
    awaitWorkListBoundary(memberIds.getFirst(), "LINKED_GROUP", groupId);
    awaitWorkListBoundary(memberIds.get(2), "LINKED_GROUP", groupId);
  }

  @Test
  void givenTwoMemberGroup_whenMemberUnlinked_thenEveryMemberUsesDirectBoundary() {
    var memberIds = createGroup(2);
    var groupId = routeRepository.findById(memberIds.getFirst()).orElseThrow().getGroupId();

    assertThat(unlink(memberIds.get(1), groupToken(groupId, 0L)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    memberIds.forEach(memberId -> awaitWorkListBoundary(memberId, "DIRECT", memberId));
  }

  @Test
  void givenAssignedGroup_whenOneMemberUnassigned_thenEveryMemberUnassigned() {
    var memberIds = createGroup(3);
    assertThat(assign(memberIds.getFirst(), TestJwtDecoderConfig.CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    assertThat(unassign(memberIds.get(1), TestJwtDecoderConfig.CASEWORKER_ID, 1L).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    memberIds.forEach(memberId -> awaitWorkListAssignee(memberId, null, 2L));
    memberIds.forEach(
        memberId -> assertThat(countDomainEvents(memberId, WorkItemUnassigned.class)).isEqualTo(1));
  }

  @Test
  void givenGroupUnassignFailsPartway_whenUnassigning_thenNoMemberChanges() {
    var memberIds = createGroup(3);
    assertThat(assign(memberIds.getFirst(), TestJwtDecoderConfig.CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var poisonMemberId = memberIds.stream().max(Comparator.naturalOrder()).orElseThrow();
    rejectEventsFor(poisonMemberId);

    var response = unassign(memberIds.getFirst(), TestJwtDecoderConfig.CASEWORKER_ID, 1L);

    assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    memberIds.forEach(
        memberId -> assertThat(countDomainEvents(memberId, WorkItemUnassigned.class)).isZero());
    memberIds.forEach(
        memberId -> awaitWorkListAssignee(memberId, TestJwtDecoderConfig.CASEWORKER_ID, 1L));
  }

  private List<UUID> createGroup(int memberCount) {
    var lastName = uniqueLastName();
    var memberIds = new ArrayList<UUID>();
    for (int index = 0; index < memberCount; index++) {
      memberIds.add(createApplication(lastName));
    }
    var leadId = memberIds.getFirst();
    assertThat(link(memberIds.get(1), leadId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var groupId =
        Objects.requireNonNull(awaitRoute(leadId, ApplicationGroupRouteKind.LINKED_GROUP));
    for (int index = 2; index < memberCount; index++) {
      assertThat(
              link(memberIds.get(index), leadId, groupToken(groupId, index - 2L)).getStatusCode())
          .isEqualTo(HttpStatus.NO_CONTENT);
    }
    memberIds.forEach(memberId -> awaitRoute(memberId, ApplicationGroupRouteKind.LINKED_GROUP));
    return memberIds;
  }
}
