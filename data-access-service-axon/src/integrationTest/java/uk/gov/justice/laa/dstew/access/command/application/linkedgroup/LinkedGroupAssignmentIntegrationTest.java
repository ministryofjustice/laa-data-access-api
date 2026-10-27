package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig.CASEWORKER_ID;
import static uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig.OTHER_CASEWORKER_ID;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.access.AbstractLinkedGroupIntegrationTest;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;

class LinkedGroupAssignmentIntegrationTest extends AbstractLinkedGroupIntegrationTest {

  @ParameterizedTest
  @EnumSource(LinkCase.class)
  void givenLinkCase_whenLinking_thenMembersHaveExpectedAssignment(LinkCase linkCase) {
    var lastName = uniqueLastName();
    var targetId = createApplication(lastName);
    var joiningId = createApplication(lastName);
    UUID existingAssignment = linkCase.initialAssignment();

    if (linkCase.isCreate()) {
      if (existingAssignment != null) {
        assign(targetId, existingAssignment, 0L);
      }
      var joiningAssignment = linkCase.joiningAssignment();
      if (joiningAssignment != null) {
        assign(joiningId, joiningAssignment, 0L);
      }

      assertThat(link(joiningId, targetId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      awaitRoute(targetId, ApplicationGroupRouteKind.LINKED_GROUP);
      assertLinkCaseAssignments(linkCase, targetId, joiningId, null);
      return;
    }

    var existingMemberId = createApplication(lastName);
    if (existingAssignment != null) {
      assign(targetId, existingAssignment, 0L);
      assign(existingMemberId, existingAssignment, 0L);
    }
    var joiningAssignment = linkCase.joiningAssignment();
    if (joiningAssignment != null) {
      assign(joiningId, joiningAssignment, 0L);
    }

    assertThat(link(existingMemberId, targetId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var groupId = awaitRoute(targetId, ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(link(joiningId, targetId, groupToken(groupId, 0L)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertLinkCaseAssignments(linkCase, targetId, joiningId, existingMemberId);
  }

  @Test
  void givenAutoGrantedTarget_whenAssignedSourceLinked_thenTargetUnchanged() {
    var lastName = uniqueLastName();
    var targetId = createSubmittedApplication(lastName);
    autoGrant(targetId);
    var sourceId = createApplication(lastName);
    assertThat(assign(sourceId, CASEWORKER_ID, 0L).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(link(sourceId, targetId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    awaitWorkListAssignee(sourceId, CASEWORKER_ID, 1L);
    assertThat(countDomainEvents(targetId, WorkItemAssigned.class)).isZero();
  }

  @Test
  void givenAutoGrantedLead_whenUnassignedMemberJoinsAssignedGroup_thenMemberGetsGroupCaseworker() {
    var lastName = uniqueLastName();
    var leadId = createSubmittedApplication(lastName);
    autoGrant(leadId);
    var assignedMemberId = createApplication(lastName);
    assertThat(assign(assignedMemberId, CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    assertThat(link(assignedMemberId, leadId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var groupId = awaitRoute(leadId, ApplicationGroupRouteKind.LINKED_GROUP);
    var joiningId = createApplication(lastName);

    assertThat(link(joiningId, leadId, groupToken(groupId, 0L)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    awaitWorkListAssignee(joiningId, CASEWORKER_ID, 1L);
  }

  @Test
  void givenNotReadyTarget_whenAssignedSourceLinked_thenTargetUnchanged() {
    var lastName = uniqueLastName();
    var targetId = createSubmittedApplication(lastName);
    var sourceId = createApplication(lastName);
    assertThat(assign(sourceId, CASEWORKER_ID, 0L).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(link(sourceId, targetId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(countDomainEvents(targetId, WorkItemAssigned.class)).isZero();
  }

  @Test
  void givenLinkSucceeded_whenReadModelsLag_thenAssignmentAlreadyCommitted() {
    var lastName = uniqueLastName();
    var targetId = createApplication(lastName);
    var sourceId = createApplication(lastName);
    assertThat(assign(targetId, CASEWORKER_ID, 0L).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(link(sourceId, targetId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(countDomainEvents(sourceId, WorkItemAssigned.class)).isEqualTo(1L);
  }

  private void assertLinkCaseAssignments(
      LinkCase linkCase, UUID targetId, UUID joiningId, UUID existingMemberId) {
    var caseworkerId =
        linkCase == LinkCase.CREATE_AC4 || linkCase == LinkCase.ADD_AC4 ? null : CASEWORKER_ID;
    var targetVersion = linkCase == LinkCase.CREATE_AC4 || linkCase == LinkCase.ADD_AC4 ? 0L : 1L;
    var joiningVersion =
        switch (linkCase) {
          case CREATE_AC4, ADD_AC4 -> 0L;
          case CREATE_AC2, ADD_AC2 -> 2L;
          default -> 1L;
        };
    awaitWorkListAssignee(targetId, caseworkerId, targetVersion);
    awaitWorkListAssignee(joiningId, caseworkerId, joiningVersion);
    if (existingMemberId != null) {
      awaitWorkListAssignee(existingMemberId, caseworkerId, targetVersion);
    }
  }

  private enum LinkCase {
    CREATE_AC1,
    CREATE_AC2,
    CREATE_AC3,
    CREATE_AC4,
    CREATE_SAME,
    ADD_AC1,
    ADD_AC2,
    ADD_AC3,
    ADD_AC4,
    ADD_SAME;

    boolean isCreate() {
      return name().startsWith("CREATE");
    }

    UUID initialAssignment() {
      return switch (this) {
        case CREATE_AC1, CREATE_AC2, CREATE_SAME, ADD_AC1, ADD_AC2, ADD_SAME -> CASEWORKER_ID;
        default -> null;
      };
    }

    UUID joiningAssignment() {
      return switch (this) {
        case CREATE_AC2, ADD_AC2 -> OTHER_CASEWORKER_ID;
        case CREATE_AC3, ADD_AC3 -> CASEWORKER_ID;
        case CREATE_SAME, ADD_SAME -> CASEWORKER_ID;
        default -> null;
      };
    }
  }
}
