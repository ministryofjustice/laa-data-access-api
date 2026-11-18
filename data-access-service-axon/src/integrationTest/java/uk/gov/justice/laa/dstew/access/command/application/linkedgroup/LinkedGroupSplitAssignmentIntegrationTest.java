package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig.CASEWORKER_ID;
import static uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig.OTHER_CASEWORKER_ID;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.access.AbstractLinkedGroupIntegrationTest;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectWorkItemAssignmentCommand;

/**
 * Verifies linking into a group whose active members do not share one caseworker. Command paths no
 * longer produce such groups, so the split is seeded by dispatching direct assignments that bypass
 * the linked-group coordinator, as data written before atomic group assignment could be.
 */
class LinkedGroupSplitAssignmentIntegrationTest extends AbstractLinkedGroupIntegrationTest {

  @Autowired private CommandGateway commandGateway;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void givenSplitGroup_whenApplicationLinked_thenConflictAndNothingChanges(
      boolean joiningAssigned) {
    var splitGroup = createSplitGroup();
    var joiningId = createApplication(splitGroup.lastName());
    if (joiningAssigned) {
      assertThat(assign(joiningId, CASEWORKER_ID, 0L).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    var response = link(joiningId, splitGroup.leadId(), groupToken(splitGroup.groupId(), 0L));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).contains("do not share one assignment");
    assertThat(routeRepository.findById(joiningId).orElseThrow().getRouteKind())
        .isEqualTo(ApplicationGroupRouteKind.STANDALONE);
    assertThat(countDomainEvents(splitGroup.groupId(), MemberAddedToGroupEvent.class)).isZero();
    assertThat(countDomainEvents(joiningId, WorkItemAssigned.class))
        .isEqualTo(joiningAssigned ? 1L : 0L);
    awaitWorkListAssignee(splitGroup.leadId(), CASEWORKER_ID, 1L);
    awaitWorkListAssignee(splitGroup.memberId(), OTHER_CASEWORKER_ID, 1L);
  }

  @Test
  void givenSplitGroupUnassigned_whenAssignedApplicationLinked_thenGroupTakesItsCaseworker() {
    var splitGroup = createSplitGroup();
    assertThat(unassign(splitGroup.leadId(), CASEWORKER_ID, 1L).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    awaitWorkListAssignee(splitGroup.leadId(), null, 2L);
    awaitWorkListAssignee(splitGroup.memberId(), null, 2L);
    var joiningId = createApplication(splitGroup.lastName());
    assertThat(assign(joiningId, OTHER_CASEWORKER_ID, 0L).getStatusCode()).isEqualTo(HttpStatus.OK);

    var response = link(joiningId, splitGroup.leadId(), groupToken(splitGroup.groupId(), 0L));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    awaitWorkListAssignee(splitGroup.leadId(), OTHER_CASEWORKER_ID, 3L);
    awaitWorkListAssignee(splitGroup.memberId(), OTHER_CASEWORKER_ID, 3L);
    awaitWorkListAssignee(joiningId, OTHER_CASEWORKER_ID, 1L);
  }

  private SplitGroup createSplitGroup() {
    var lastName = uniqueLastName();
    var leadId = createApplication(lastName);
    var memberId = createApplication(lastName);
    assertThat(link(memberId, leadId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var groupId =
        Objects.requireNonNull(awaitRoute(leadId, ApplicationGroupRouteKind.LINKED_GROUP));
    awaitRoute(memberId, ApplicationGroupRouteKind.LINKED_GROUP);

    for (var assignment :
        List.of(
            new Assignment(leadId, CASEWORKER_ID), new Assignment(memberId, OTHER_CASEWORKER_ID))) {
      commandGateway.sendAndWait(
          new DirectWorkItemAssignmentCommand(
              assignment.applicationId(),
              assignment.caseworkerId(),
              0L,
              "{}",
              "Assigned",
              Instant.now()));
    }
    awaitWorkListAssignee(leadId, CASEWORKER_ID, 1L);
    awaitWorkListAssignee(memberId, OTHER_CASEWORKER_ID, 1L);
    return new SplitGroup(lastName, groupId, leadId, memberId);
  }

  private record Assignment(UUID applicationId, UUID caseworkerId) {}

  private record SplitGroup(String lastName, UUID groupId, UUID leadId, UUID memberId) {}
}
