package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentReclaimedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupMemberAssignmentInheritedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.config.interceptor.RequestMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationProjection;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryProjection;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexProjection;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListProjection;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/**
 * Direct PostgreSQL projection integration tests for linked-group assignment ordering and replay.
 */
class LinkedGroupAssignmentProjectionIntegrationTest extends BaseIntegrationTest {

  private static final UUID CASEWORKER = TestJwtDecoderConfig.CASEWORKER_ID;
  private static final Instant TIME = Instant.parse("2026-08-01T10:00:00Z");

  @Autowired private ApplicationProjection applicationProjection;
  @Autowired private ApplicationHistoryProjection historyProjection;
  @Autowired private ApplicationListIndexProjection listIndexProjection;
  @Autowired private WorkListProjection workListProjection;
  @Autowired private ApplicationDataStore applicationDataStore;
  @Autowired private ApplicationReadRepository applications;
  @MockitoBean private QueryUpdateEmitter queryUpdateEmitter;

  @ParameterizedTest
  @EnumSource(EventOrder.class)
  void givenReadyAndGroupAssignment_whenEitherOrder_thenSameReadModels(EventOrder order) {
    UUID a = seedApplication();
    UUID b = seedApplication();
    UUID groupId = UUID.randomUUID();
    if (order == EventOrder.READY_FIRST) {
      project(ready(a));
      project(ready(b));
      project(createdGroup(groupId, a, List.of(a, b)));
      project(assigned(groupId, CASEWORKER, 1));
    } else {
      project(ready(a));
      project(createdGroup(groupId, a, List.of(a, b)));
      project(assigned(groupId, CASEWORKER, 1));
      project(ready(b));
      project(new LinkedGroupMemberAssignmentInheritedEvent(groupId, b, CASEWORKER, 1, TIME));
    }

    for (UUID id : List.of(a, b)) {
      assertWorkListRow(id, CASEWORKER, groupId, 1);
      assertThat(applications.findById(id).orElseThrow().getCaseworkerId()).isEqualTo(CASEWORKER);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT caseworker_id FROM axon.application_list_index "
                      + "WHERE application_id = ?",
                  UUID.class,
                  id))
          .isEqualTo(CASEWORKER);
      assertThat(countHistory(id, "ASSIGN_APPLICATION_TO_CASEWORKER")).isEqualTo(1);
    }
  }

  @Test
  void givenDecidedMember_whenGroupUnassignedAndDissolved_thenKeepsCaseworker() {
    UUID a = seedApplication();
    UUID b = seedApplication();
    UUID groupId = UUID.randomUUID();
    project(ready(a));
    project(ready(b));
    project(createdGroup(groupId, a, List.of(a, b)));
    project(assigned(groupId, CASEWORKER, 1));
    project(
        new ApplicationDecisionMadeEvent(
            b, 2, 0, "GRANTED", AutoGrantedState.MANUAL, TIME.plusSeconds(1)));
    project(new LinkedGroupUnassignedEvent(groupId, 2, TIME.plusSeconds(2)));
    project(
        new LinkedApplicationGroupDissolvedEvent(
            groupId, a, b, List.of(a, b), 1, null, 2, TIME.plusSeconds(3)));
    project(
        new ApplicationAssignmentReclaimedFromGroupEvent(a, groupId, null, 3, TIME.plusSeconds(4)));
    project(
        new ApplicationAssignmentReclaimedFromGroupEvent(b, groupId, null, 3, TIME.plusSeconds(4)));

    assertThat(applications.findById(b).orElseThrow().getCaseworkerId()).isEqualTo(CASEWORKER);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT caseworker_id FROM axon.application_list_index WHERE application_id = ?",
                UUID.class,
                b))
        .isEqualTo(CASEWORKER);
    assertNoWorkListRow(b);
    assertThat(countHistory(b, "UNASSIGN_APPLICATION_TO_CASEWORKER")).isZero();

    assertWorkListRow(a, null, a, 3);
    assertThat(applications.findById(a).orElseThrow().getCaseworkerId()).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT caseworker_id FROM axon.application_list_index WHERE application_id = ?",
                UUID.class,
                a))
        .isNull();
    assertThat(countHistory(a, "UNASSIGN_APPLICATION_TO_CASEWORKER")).isEqualTo(1);
  }

  @Test
  void givenTrackingRows_whenProjectionsReset_thenTrackingTablesEmpty() {
    UUID a = seedApplication();
    UUID b = seedApplication();
    UUID groupId = UUID.randomUUID();
    project(ready(a));
    project(createdGroup(groupId, a, List.of(a, b)));
    project(assigned(groupId, CASEWORKER, 1));

    applicationProjection.reset();
    historyProjection.reset();
    listIndexProjection.reset();
    workListProjection.reset();

    for (String table :
        List.of(
            "application_projection_assignment_application",
            "application_list_index_assignment_application",
            "work_list_assignment_application",
            "application_history_assignment_application")) {
      assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM axon." + table, Integer.class))
          .as(table)
          .isZero();
    }
  }

  private enum EventOrder {
    READY_FIRST,
    ASSIGNMENT_FIRST
  }

  private UUID seedApplication() {
    UUID id = UUID.randomUUID();
    ApplicationCreationDetails details = applicationCreationDetails(id);
    applicationDataStore.append(id, 0L, details);
    project(applicationCreatedEvent(id, details));
    return id;
  }

  private ApplicationReadyForManualAssessmentEvent ready(UUID id) {
    return new ApplicationReadyForManualAssessmentEvent(id, 1, 0, 0, TIME);
  }

  private LinkedApplicationGroupCreatedEvent createdGroup(
      UUID groupId, UUID leadId, List<UUID> members) {
    return new LinkedApplicationGroupCreatedEvent(groupId, leadId, members, TIME);
  }

  private LinkedGroupAssignedEvent assigned(UUID groupId, UUID caseworker, long version) {
    return new LinkedGroupAssignedEvent(groupId, caseworker, version, TIME);
  }

  private void project(Object event) {
    EventMessage message = message(event);
    if (event instanceof ApplicationCreatedEvent created) {
      applicationProjection.on(created, queryUpdateEmitter);
      listIndexProjection.on(created, message);
      historyProjection.on(created, message);
    } else if (event instanceof ApplicationReadyForManualAssessmentEvent ready) {
      workListProjection.on(ready, message);
      applicationProjection.on(ready, queryUpdateEmitter);
      listIndexProjection.on(ready, message);
      historyProjection.on(ready);
    } else if (event instanceof LinkedApplicationGroupCreatedEvent created) {
      workListProjection.on(created, message);
      applicationProjection.on(created);
      listIndexProjection.on(created, message);
      historyProjection.on(created, message);
    } else if (event instanceof LinkedGroupAssignedEvent assigned) {
      workListProjection.on(assigned, message);
      applicationProjection.on(assigned);
      listIndexProjection.on(assigned, message);
      historyProjection.on(assigned, message);
    } else if (event instanceof LinkedGroupMemberAssignmentInheritedEvent inherited) {
      workListProjection.on(inherited, message);
      applicationProjection.on(inherited);
      listIndexProjection.on(inherited, message);
      historyProjection.on(inherited, message);
    } else if (event instanceof ApplicationDecisionMadeEvent decision) {
      workListProjection.on(decision);
      applicationProjection.on(decision, queryUpdateEmitter);
      listIndexProjection.on(decision, message);
      historyProjection.on(decision, message);
    } else if (event instanceof LinkedGroupUnassignedEvent unassigned) {
      workListProjection.on(unassigned, message);
      applicationProjection.on(unassigned);
      listIndexProjection.on(unassigned, message);
      historyProjection.on(unassigned, message);
    } else if (event instanceof LinkedApplicationGroupDissolvedEvent dissolved) {
      workListProjection.on(dissolved);
      applicationProjection.on(dissolved);
      listIndexProjection.on(dissolved, message);
      historyProjection.on(dissolved, message);
    } else if (event instanceof ApplicationAssignmentReclaimedFromGroupEvent reclaimed) {
      workListProjection.on(reclaimed, message);
      applicationProjection.on(reclaimed);
      listIndexProjection.on(reclaimed, message);
      historyProjection.on(reclaimed, message);
    } else {
      throw new IllegalArgumentException("Unsupported projection event " + event.getClass());
    }
  }

  private EventMessage message(Object payload) {
    return new GenericEventMessage(
        UUID.randomUUID().toString(),
        new MessageType(payload.getClass()),
        payload,
        Map.of(RequestMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY, "CIVIL_APPLY"),
        TIME);
  }

  private void assertWorkListRow(UUID item, UUID assignee, UUID owner, long version) {
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT assignee_id, assignment_version, assignment_boundary_type, "
                + "assignment_boundary_id, group_id FROM axon.work_list_item WHERE item_id = ?",
            item);
    assertThat(row.get("assignee_id")).isEqualTo(assignee);
    assertThat(((Number) row.get("assignment_version")).longValue()).isEqualTo(version);
    assertThat(row.get("assignment_boundary_type"))
        .isEqualTo(owner.equals(item) ? "DIRECT" : "LINKED_GROUP");
    assertThat(row.get("assignment_boundary_id")).isEqualTo(owner);
    assertThat(row.get("group_id")).isEqualTo(owner.equals(item) ? null : owner);
  }

  private void assertNoWorkListRow(UUID item) {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.work_list_item WHERE item_id = ?", Integer.class, item))
        .isZero();
  }

  private int countHistory(UUID id, String eventType) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.application_history WHERE application_id = ? AND event_type = ?",
        Integer.class,
        id,
        eventType);
  }
}
