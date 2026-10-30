package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.access.version.VersionToken;

/** Verifies that assignment and membership writers serialize on the durable group route. */
class LinkedGroupAssignmentConcurrencyIntegrationTest extends BaseIntegrationTest {

  private static final UUID C1 = TestJwtDecoderConfig.CASEWORKER_ID;
  private static final UUID C2 = TestJwtDecoderConfig.OTHER_CASEWORKER_ID;
  private static final int ADVISORY_KEY = 847134;

  @Autowired private DataSource dataSource;

  @ParameterizedTest
  @EnumSource(RaceOrder.class)
  void givenAssignAndAddMember_whenRacing_thenOneConsistentOutcome(RaceOrder order)
      throws Exception {
    UUID a = createApplication();
    UUID b = createApplication();
    assertThat(link(b, a, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    UUID groupId = awaitGroup(a);
    UUID joiningMember = createApplication();
    assertThat(assign(joiningMember, C2, assignmentToken(joiningMember, 0)).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    boolean assignmentFirst = order == RaceOrder.ASSIGN_FIRST;
    String eventType =
        assignmentFirst
            ? LinkedGroupAssignedEvent.class.getName()
            : MemberAddedToGroupEvent.class.getName();
    try (EventGate gate = pauseEvent(eventType)) {
      CompletableFuture<ResponseEntity<String>> first =
          CompletableFuture.supplyAsync(
              () ->
                  assignmentFirst
                      ? assign(a, C1, assignmentToken(groupId, 0))
                      : link(joiningMember, a, linkedGroupToken(groupId, 0)));
      int firstPid = awaitBlockedBy(gate.backendPid());
      CompletableFuture<ResponseEntity<String>> second =
          CompletableFuture.supplyAsync(
              () ->
                  assignmentFirst
                      ? link(joiningMember, a, linkedGroupToken(groupId, 0))
                      : assign(a, C1, assignmentToken(groupId, 0)));
      awaitBlockedBy(firstPid);
      assertThat(second).isNotDone();
      gate.release();

      ResponseEntity<String> firstResponse = first.get(15, TimeUnit.SECONDS);
      ResponseEntity<String> secondResponse = second.get(15, TimeUnit.SECONDS);
      if (assignmentFirst) {
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        for (UUID member : List.of(a, b, joiningMember)) {
          awaitWorkListRow(member, C1, groupId, 1);
        }
      } else {
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondResponse.getBody()).contains("the assignment version is stale");
        for (UUID member : List.of(a, b, joiningMember)) {
          awaitWorkListRow(member, C2, groupId, 1);
        }
      }
    }

    assertThat(countEvents(groupId, LinkedGroupAssignedEvent.class)).isEqualTo(1);
    assertThat(countEvents(a, WorkItemAssigned.class)).isZero();
    assertThat(countEvents(b, WorkItemAssigned.class)).isZero();
  }

  private enum RaceOrder {
    ASSIGN_FIRST,
    ADD_FIRST
  }

  private UUID createApplication() {
    UUID id = UUID.randomUUID();
    var request = validCreateApplicationRequest(id, UUID.randomUUID());
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    Map<?, ?> originalProvider = (Map<?, ?>) content.get("provider");
    Map<String, Object> provider = new HashMap<>();
    originalProvider.forEach((key, value) -> provider.put(key.toString(), value));
    provider.put("officeCode", "1A001B");
    content.put("provider", provider);
    request.setApplicationContent(content);
    ResponseEntity<String> created =
        restTemplate.postForEntity(
            url("/api/v0/applications"), new HttpEntity<>(request, headers()), String.class);
    assertThat(created.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    ResponseEntity<String> ready =
        restTemplate.exchange(
            url("/api/v0/applications/" + id + "/auto-grant-outcome"),
            HttpMethod.PATCH,
            new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL), headers()),
            String.class);
    assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    return id;
  }

  private ResponseEntity<String> link(UUID source, UUID target, String token) {
    ApplicationLinkRequest request = new ApplicationLinkRequest(target, ApplicationLinkType.FAMILY);
    request.setLinkedGroupVersion(token);
    return restTemplate.exchange(
        url("/api/v0/applications/" + source + "/link"),
        HttpMethod.POST,
        new HttpEntity<>(request, headers()),
        String.class);
  }

  private ResponseEntity<String> assign(UUID item, UUID caseworker, String token) {
    return restTemplate.exchange(
        url("/api/v0/work-list/" + item + "/assign"),
        HttpMethod.POST,
        new HttpEntity<>(new WorkListAssignRequest(token), headersFor(caseworker)),
        String.class);
  }

  private UUID awaitGroup(UUID applicationId) {
    return await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () ->
                jdbcTemplate
                    .query(
                        "SELECT group_id FROM axon.application_group_route "
                            + "WHERE application_id = ? AND route_kind = 'LINKED_GROUP'",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        applicationId)
                    .stream()
                    .findFirst()
                    .orElse(null),
            group -> group != null);
  }

  private String linkedGroupToken(UUID groupId, long version) {
    return VersionToken.linkedGroup(groupId, version).encode();
  }

  private String assignmentToken(UUID ownerId, long version) {
    return VersionToken.workItemAssignment(ownerId, version).encode();
  }

  private void awaitWorkListRow(UUID item, UUID assignee, UUID groupId, long version) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Map<String, Object> row =
                  jdbcTemplate.queryForMap(
                      "SELECT assignee_id, assignment_version, assignment_boundary_type, "
                          + "assignment_boundary_id, group_id FROM axon.work_list_item "
                          + "WHERE item_id = ?",
                      item);
              assertThat(row.get("assignee_id")).isEqualTo(assignee);
              assertThat(((Number) row.get("assignment_version")).longValue()).isEqualTo(version);
              assertThat(row.get("assignment_boundary_type")).isEqualTo("LINKED_GROUP");
              assertThat(row.get("assignment_boundary_id")).isEqualTo(groupId);
              assertThat(row.get("group_id")).isEqualTo(groupId);
            });
  }

  private long countEvents(UUID aggregateId, Class<?> eventType) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.domain_event_entry "
            + "WHERE aggregate_identifier = ? AND payload_type = ?",
        Long.class,
        aggregateId.toString(),
        eventType.getName());
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
    return await()
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
