package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.AbstractLinkedGroupIntegrationTest;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.access.testutils.EventTypeNames;

/** Verifies assignment and membership changes serialize on the locked group routes. */
class LinkedGroupAssignmentConcurrencyIntegrationTest extends AbstractLinkedGroupIntegrationTest {

  private static final int ADVISORY_KEY = 847134;

  @Autowired private DataSource dataSource;

  @ParameterizedTest
  @EnumSource(RaceOrder.class)
  void givenAssignAndAddMember_whenRacing_thenGroupEndsOnOneCaseworker(RaceOrder order)
      throws Exception {
    var leadId = createApplication(uniqueLastName());
    var existingMemberId = createApplication(uniqueLastName());
    assertThat(link(existingMemberId, leadId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var groupId =
        Objects.requireNonNull(awaitRoute(leadId, ApplicationGroupRouteKind.LINKED_GROUP));
    var joiningMemberId = createApplication(uniqueLastName());
    assertThat(
            assign(joiningMemberId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    var assignmentFirst = order == RaceOrder.ASSIGN_FIRST;
    var eventType =
        assignmentFirst
            ? EventTypeNames.of(WorkItemAssigned.class)
            : EventTypeNames.of(MemberAddedToGroupEvent.class);
    try (var gate = pauseEvent(eventType)) {
      final CompletableFuture<ResponseEntity<String>> first =
          CompletableFuture.supplyAsync(
              () ->
                  assignmentFirst
                      ? assign(leadId, TestJwtDecoderConfig.CASEWORKER_ID, 0L)
                      : link(joiningMemberId, leadId, groupToken(groupId, 0)));
      var firstPid = awaitBlockedBy(gate.backendPid());
      CompletableFuture<ResponseEntity<String>> second =
          CompletableFuture.supplyAsync(
              () ->
                  assignmentFirst
                      ? link(joiningMemberId, leadId, groupToken(groupId, 0))
                      : assign(leadId, TestJwtDecoderConfig.CASEWORKER_ID, 0L));
      awaitBlockedBy(firstPid);
      assertThat(second).isNotDone();
      gate.release();

      var firstResponse = first.get(15, TimeUnit.SECONDS);
      var secondResponse = second.get(15, TimeUnit.SECONDS);
      if (assignmentFirst) {
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        awaitWorkListAssignee(leadId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
        awaitWorkListAssignee(existingMemberId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
        awaitWorkListAssignee(joiningMemberId, TestJwtDecoderConfig.CASEWORKER_ID, 2L);
      } else {
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        awaitWorkListAssignee(leadId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 1L);
        awaitWorkListAssignee(existingMemberId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 1L);
        awaitWorkListAssignee(joiningMemberId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 1L);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(AddressedFirst.class)
  void givenUnassignedGroup_whenTwoAssignsRace_thenOneCaseworkerWins(AddressedFirst addressedFirst)
      throws Exception {
    var group = createTwoMemberGroup();
    var leadId = group.leadId();
    var memberId = group.memberIds().get(1);
    var winnerItemId = addressedFirst == AddressedFirst.LEAD ? leadId : memberId;
    var loserItemId = addressedFirst == AddressedFirst.LEAD ? memberId : leadId;

    try (var gate = pauseEvent(EventTypeNames.of(WorkItemAssigned.class))) {
      final CompletableFuture<ResponseEntity<String>> first =
          CompletableFuture.supplyAsync(
              () -> assign(winnerItemId, TestJwtDecoderConfig.CASEWORKER_ID, 0L));
      var firstPid = awaitBlockedBy(gate.backendPid());
      CompletableFuture<ResponseEntity<String>> second =
          CompletableFuture.supplyAsync(
              () -> assign(loserItemId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 0L));
      awaitBlockedBy(firstPid);
      assertThat(second).isNotDone();
      gate.release();

      assertThat(first.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
      var secondResponse = second.get(15, TimeUnit.SECONDS);
      assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(secondResponse.getBody()).contains("the assignment version is stale");
    }

    for (var applicationId : group.memberIds()) {
      awaitWorkListAssignee(applicationId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
      awaitWorkListBoundary(applicationId, "LINKED_GROUP", group.groupId());
      assertThat(countDomainEvents(applicationId, WorkItemAssigned.class)).isOne();
    }
  }

  @ParameterizedTest
  @EnumSource(AssignUnassignOrder.class)
  void givenGroup_whenAssignAndUnassignRace_thenGroupStaysUniform(AssignUnassignOrder order)
      throws Exception {
    var group = createTwoMemberGroup();
    var leadId = group.leadId();
    var memberId = group.memberIds().get(1);
    var assignFirst = order == AssignUnassignOrder.ASSIGN_FIRST;
    if (!assignFirst) {
      assertThat(assign(leadId, TestJwtDecoderConfig.CASEWORKER_ID, 0L).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      group
          .memberIds()
          .forEach(
              applicationId ->
                  awaitWorkListAssignee(applicationId, TestJwtDecoderConfig.CASEWORKER_ID, 1L));
    }
    var startVersion = assignFirst ? 0L : 1L;
    var gatedEvent =
        assignFirst
            ? EventTypeNames.of(WorkItemAssigned.class)
            : EventTypeNames.of(WorkItemUnassigned.class);

    try (var gate = pauseEvent(gatedEvent)) {
      final CompletableFuture<ResponseEntity<String>> first =
          CompletableFuture.supplyAsync(
              () ->
                  assignFirst
                      ? assign(leadId, TestJwtDecoderConfig.CASEWORKER_ID, startVersion)
                      : unassign(leadId, TestJwtDecoderConfig.CASEWORKER_ID, startVersion));
      var firstPid = awaitBlockedBy(gate.backendPid());
      CompletableFuture<ResponseEntity<String>> second =
          CompletableFuture.supplyAsync(
              () ->
                  assignFirst
                      ? unassign(memberId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, startVersion)
                      : assign(memberId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, startVersion));
      awaitBlockedBy(firstPid);
      assertThat(second).isNotDone();
      gate.release();

      assertThat(first.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
      var secondResponse = second.get(15, TimeUnit.SECONDS);
      assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(secondResponse.getBody()).contains("the assignment version is stale");
    }

    var expectedAssignee = assignFirst ? TestJwtDecoderConfig.CASEWORKER_ID : null;
    var expectedVersion = startVersion + 1;
    for (var applicationId : group.memberIds()) {
      awaitWorkListAssignee(applicationId, expectedAssignee, expectedVersion);
      assertThat(countDomainEvents(applicationId, WorkItemAssigned.class)).isOne();
      assertThat(countDomainEvents(applicationId, WorkItemUnassigned.class))
          .isEqualTo(assignFirst ? 0L : 1L);
    }
  }

  @Test
  void givenAssignedGroup_whenTwoUnassignsRace_thenOneSucceeds() throws Exception {
    var group = createTwoMemberGroup();
    var leadId = group.leadId();
    var memberId = group.memberIds().get(1);
    assertThat(assign(leadId, TestJwtDecoderConfig.CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    group
        .memberIds()
        .forEach(
            applicationId ->
                awaitWorkListAssignee(applicationId, TestJwtDecoderConfig.CASEWORKER_ID, 1L));

    try (var gate = pauseEvent(EventTypeNames.of(WorkItemUnassigned.class))) {
      final CompletableFuture<ResponseEntity<String>> first =
          CompletableFuture.supplyAsync(
              () -> unassign(leadId, TestJwtDecoderConfig.CASEWORKER_ID, 1L));
      var firstPid = awaitBlockedBy(gate.backendPid());
      CompletableFuture<ResponseEntity<String>> second =
          CompletableFuture.supplyAsync(
              () -> unassign(memberId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 1L));
      awaitBlockedBy(firstPid);
      assertThat(second).isNotDone();
      gate.release();

      assertThat(first.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
      var secondResponse = second.get(15, TimeUnit.SECONDS);
      assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(secondResponse.getBody()).contains("the assignment version is stale");
    }

    for (var applicationId : group.memberIds()) {
      awaitWorkListAssignee(applicationId, null, 2L);
      assertThat(countDomainEvents(applicationId, WorkItemUnassigned.class)).isOne();
    }
  }

  static Stream<Arguments> assignUnlinkCases() {
    return Stream.of(UnlinkTopology.values())
        .flatMap(
            topology ->
                Stream.of(AssignUnlinkOrder.values()).map(order -> Arguments.of(topology, order)));
  }

  @ParameterizedTest
  @MethodSource("assignUnlinkCases")
  void givenGroup_whenAssignAndUnlinkRace_thenNoPartialAssignment(
      UnlinkTopology topology, AssignUnlinkOrder order) throws Exception {
    var threeMembers = topology == UnlinkTopology.THREE_MEMBERS;
    var group = createGroup(threeMembers ? 3 : 2);
    var leadId = group.leadId();
    var leaverId = group.memberIds().get(1);
    var groupVersion = threeMembers ? 1L : 0L;
    var assignFirst = order == AssignUnlinkOrder.ASSIGN_FIRST;
    var unlinkEvent =
        threeMembers
            ? EventTypeNames.of(MemberRemovedFromGroupEvent.class)
            : EventTypeNames.of(LinkedApplicationGroupDissolvedEvent.class);
    var gatedEvent = assignFirst ? EventTypeNames.of(WorkItemAssigned.class) : unlinkEvent;

    try (var gate = pauseEvent(gatedEvent)) {
      final CompletableFuture<ResponseEntity<String>> first =
          CompletableFuture.supplyAsync(
              () ->
                  assignFirst
                      ? assign(leadId, TestJwtDecoderConfig.CASEWORKER_ID, 0L)
                      : unlink(leaverId, groupToken(group.groupId(), groupVersion)));
      var firstPid = awaitBlockedBy(gate.backendPid());
      CompletableFuture<ResponseEntity<String>> second =
          CompletableFuture.supplyAsync(
              () ->
                  assignFirst
                      ? unlink(leaverId, groupToken(group.groupId(), groupVersion))
                      : assign(leadId, TestJwtDecoderConfig.CASEWORKER_ID, 0L));
      awaitBlockedBy(firstPid);
      assertThat(second).isNotDone();
      gate.release();

      var assignResponse =
          assignFirst ? first.get(15, TimeUnit.SECONDS) : second.get(15, TimeUnit.SECONDS);
      var unlinkResponse =
          assignFirst ? second.get(15, TimeUnit.SECONDS) : first.get(15, TimeUnit.SECONDS);
      assertThat(unlinkResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      if (assignFirst) {
        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      } else {
        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(assignResponse.getBody()).contains("changed; retry the request");
      }
    }

    awaitRoute(leaverId, ApplicationGroupRouteKind.STANDALONE);
    awaitWorkListBoundary(leaverId, "DIRECT", leaverId);
    var remainingIds =
        group.memberIds().stream().filter(candidateId -> !candidateId.equals(leaverId)).toList();
    for (var remainingId : remainingIds) {
      if (threeMembers) {
        awaitWorkListBoundary(remainingId, "LINKED_GROUP", group.groupId());
      } else {
        awaitWorkListBoundary(remainingId, "DIRECT", remainingId);
      }
    }

    if (assignFirst) {
      for (var applicationId : group.memberIds()) {
        awaitWorkListAssignee(applicationId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
        assertThat(countDomainEvents(applicationId, WorkItemAssigned.class)).isOne();
      }
      return;
    }

    for (var applicationId : group.memberIds()) {
      awaitWorkListAssignee(applicationId, null, 0L);
      assertThat(countDomainEvents(applicationId, WorkItemAssigned.class)).isZero();
    }
    assertThat(assign(leaverId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    awaitWorkListAssignee(leaverId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 1L);
    for (var remainingId : remainingIds) {
      awaitWorkListAssignee(remainingId, null, 0L);
      assertThat(countDomainEvents(remainingId, WorkItemAssigned.class)).isZero();
    }
  }

  @Test
  void givenDeadlockOnFirstAttempt_whenAssigned_thenRetriedAndGroupAssigned() {
    var group = createTwoMemberGroup();
    failAssignmentEventsWithDeadlock(group.leadId(), false);

    assertThat(assign(group.leadId(), TestJwtDecoderConfig.CASEWORKER_ID, 0L).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    for (var applicationId : group.memberIds()) {
      awaitWorkListAssignee(applicationId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
      assertThat(countDomainEvents(applicationId, WorkItemAssigned.class)).isOne();
    }
  }

  @Test
  void givenDeadlockOnEveryAttempt_whenAssigned_thenConflictAndNothingChanges() {
    var group = createTwoMemberGroup();
    failAssignmentEventsWithDeadlock(group.leadId(), true);

    var response = assign(group.leadId(), TestJwtDecoderConfig.CASEWORKER_ID, 0L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).contains("retry the request");
    for (var applicationId : group.memberIds()) {
      assertThat(countDomainEvents(applicationId, WorkItemAssigned.class)).isZero();
      awaitWorkListAssignee(applicationId, null, 0L);
    }
  }

  private record GroupFixture(UUID groupId, List<UUID> memberIds) {
    UUID leadId() {
      return memberIds.getFirst();
    }
  }

  private enum AddressedFirst {
    LEAD,
    MEMBER
  }

  private enum AssignUnassignOrder {
    ASSIGN_FIRST,
    UNASSIGN_FIRST
  }

  private enum UnlinkTopology {
    THREE_MEMBERS,
    TWO_MEMBERS
  }

  private enum AssignUnlinkOrder {
    ASSIGN_FIRST,
    UNLINK_FIRST
  }

  private GroupFixture createTwoMemberGroup() {
    return createGroup(2);
  }

  private GroupFixture createGroup(int memberCount) {
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
    return new GroupFixture(groupId, List.copyOf(memberIds));
  }

  private enum RaceOrder {
    ASSIGN_FIRST,
    ADD_FIRST
  }

  private void failAssignmentEventsWithDeadlock(UUID aggregateId, boolean everyAttempt) {
    jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS axon.fail_test_assignment_lock_seq");
    jdbcTemplate.execute(
        """
        CREATE OR REPLACE FUNCTION axon.fail_test_assignment_lock()
        RETURNS trigger AS $$
        BEGIN
          IF NEW.aggregate_identifier = '%s'
              AND NEW.payload_type LIKE '%%WorkItemAssigned' THEN
            IF %s OR nextval('axon.fail_test_assignment_lock_seq') = 1 THEN
              RAISE EXCEPTION 'forced deadlock' USING ERRCODE = '40P01';
            END IF;
          END IF;
          RETURN NEW;
        END;
        $$ LANGUAGE plpgsql
        """
            .formatted(aggregateId, everyAttempt ? "TRUE" : "FALSE"));
    jdbcTemplate.execute(
        """
        CREATE TRIGGER fail_test_assignment_lock
        BEFORE INSERT ON axon.domain_event_entry
        FOR EACH ROW EXECUTE FUNCTION axon.fail_test_assignment_lock()
        """);
  }

  @AfterEach
  void dropAssignmentLockFailureTrigger() {
    jdbcTemplate.execute(
        "DROP TRIGGER IF EXISTS fail_test_assignment_lock ON axon.domain_event_entry");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS axon.fail_test_assignment_lock()");
    jdbcTemplate.execute("DROP SEQUENCE IF EXISTS axon.fail_test_assignment_lock_seq");
  }

  private EventGate pauseEvent(String eventType) throws SQLException {
    Connection connection = dataSource.getConnection();
    try {
      int backendPid;
      try (var statement = connection.createStatement();
          var result =
              statement.executeQuery(
                  "SELECT pg_backend_pid(), pg_advisory_lock(" + ADVISORY_KEY + ")")) {
        assertThat(result.next()).isTrue();
        backendPid = result.getInt(1);
      }
      jdbcTemplate.execute(
          """
          CREATE OR REPLACE FUNCTION axon.gate_test_linked_group_assignment_event()
          RETURNS trigger AS $$
          BEGIN
            IF NEW.payload_type LIKE '%%%s' THEN
              PERFORM pg_advisory_xact_lock(%d);
            END IF;
            RETURN NEW;
          END;
          $$ LANGUAGE plpgsql
          """
              .formatted(eventType, ADVISORY_KEY));
      jdbcTemplate.execute(
          """
          CREATE TRIGGER gate_test_linked_group_assignment_event
          BEFORE INSERT ON axon.domain_event_entry
          FOR EACH ROW EXECUTE FUNCTION axon.gate_test_linked_group_assignment_event()
          """);
      return new EventGate(connection, backendPid);
    } catch (RuntimeException | SQLException failure) {
      connection.close();
      throw failure;
    }
  }

  private int awaitBlockedBy(int backendPid) {
    return Awaitility.await()
        .alias("PostgreSQL transaction blocked by backend " + backendPid)
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                jdbcTemplate.queryForList(
                    "SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
                    Integer.class,
                    backendPid),
            blocked -> !blocked.isEmpty())
        .getFirst();
  }

  private record EventGate(Connection connection, int backendPid) implements AutoCloseable {
    void release() throws SQLException {
      try (var statement = connection.createStatement()) {
        statement.execute("SELECT pg_advisory_unlock(" + ADVISORY_KEY + ")");
      }
    }

    @Override
    public void close() throws SQLException {
      try {
        release();
        try (var statement = connection.createStatement()) {
          statement.execute(
              "DROP TRIGGER IF EXISTS gate_test_linked_group_assignment_event "
                  + "ON axon.domain_event_entry");
          statement.execute(
              "DROP FUNCTION IF EXISTS axon.gate_test_linked_group_assignment_event()");
        }
      } finally {
        connection.close();
      }
    }
  }
}
