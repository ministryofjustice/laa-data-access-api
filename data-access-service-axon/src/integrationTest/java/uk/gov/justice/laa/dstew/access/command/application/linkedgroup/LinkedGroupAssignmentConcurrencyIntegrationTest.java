package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.AbstractLinkedGroupIntegrationTest;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

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
            ? WorkItemAssigned.class.getName()
            : MemberAddedToGroupEvent.class.getName();
    try (var gate = pauseEvent(eventType)) {
      CompletableFuture<ResponseEntity<String>> first =
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

  private enum RaceOrder {
    ASSIGN_FIRST,
    ADD_FIRST
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
