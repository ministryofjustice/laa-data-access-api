package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentReclaimedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupMemberAssignmentInheritedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.LinkedGroupChangeRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListUnassignRequest;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.access.version.VersionToken;

/** End-to-end linked-group assignment tests over HTTP, PostgreSQL and Axon. */
class LinkedGroupAssignmentIntegrationTest extends BaseIntegrationTest {

  private static final UUID C1 = TestJwtDecoderConfig.CASEWORKER_ID;
  private static final UUID C2 = TestJwtDecoderConfig.OTHER_CASEWORKER_ID;

  @Autowired private ObjectMapper objectMapper;

  private final Map<UUID, UUID> proceedingIds = new HashMap<>();

  @Test
  void givenLinkedReadyItems_whenOneAssigned_thenOneGroupEventAndAllShowIt() {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    UUID c = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    link(c, a, linkedGroupToken(groupId, 0));

    for (UUID member : List.of(a, b, c)) {
      awaitWorkListRow(member, null, groupId, 0);
    }
    assertThat(assign(b, C1, assignmentToken(groupId, 0)).getStatusCode()).isEqualTo(HttpStatus.OK);

    for (UUID member : List.of(a, b, c)) {
      awaitWorkListRow(member, C1, groupId, 1);
      awaitShownCaseworker(member, C1);
      awaitHistoryCount(member, "ASSIGN_APPLICATION_TO_CASEWORKER", 1);
      assertThat(countEvents(member, WorkItemAssigned.class)).isZero();
    }
    assertThat(countEvents(groupId, LinkedGroupAssignedEvent.class)).isEqualTo(1);
  }

  @Test
  void givenAssignedGroup_whenAssignedAgain_thenConflictWithoutEvent() {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);

    assertThat(assign(a, C1, assignmentToken(groupId, 0)).getStatusCode()).isEqualTo(HttpStatus.OK);
    ResponseEntity<String> second = assign(b, C2, assignmentToken(groupId, 1));

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(second.getBody()).contains("it is already assigned");
    assertThat(countEvents(groupId, LinkedGroupAssignedEvent.class)).isEqualTo(1);
  }

  @Test
  void givenApplicationToken_whenApplicationJoinsGroup_thenTokenRejected() {
    UUID a = createApplication(true);
    assertThat(assign(a, C1, assignmentToken(a, 0)).getStatusCode()).isEqualTo(HttpStatus.OK);
    UUID b = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    awaitWorkListRow(a, C1, groupId, 1);
    awaitWorkListRow(b, C1, groupId, 1);

    ResponseEntity<String> stale = unassign(a, assignmentToken(a, 1));
    assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(stale.getBody()).contains("the assignment version is stale");
    assertThat(unassign(a, assignmentToken(groupId, 1)).getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListRow(a, null, groupId, 2);
    awaitWorkListRow(b, null, groupId, 2);
  }

  @Test
  void givenAssignedGroup_whenUnassigned_thenOneGroupEventAndAllCleared() {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    assign(a, C1, assignmentToken(groupId, 0));

    assertThat(unassign(b, assignmentToken(groupId, 1)).getStatusCode()).isEqualTo(HttpStatus.OK);
    for (UUID member : List.of(a, b)) {
      awaitWorkListRow(member, null, groupId, 2);
      awaitHistoryCount(member, "UNASSIGN_APPLICATION_TO_CASEWORKER", 1);
      assertThat(countEvents(member, WorkItemUnassigned.class)).isZero();
    }
    assertThat(countEvents(groupId, LinkedGroupUnassignedEvent.class)).isEqualTo(1);
  }

  @Test
  void givenUnassignedGroup_whenUnassigned_thenConflict() {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);

    ResponseEntity<String> response = unassign(a, assignmentToken(groupId, 0));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).contains("it is already unassigned");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("creationRules")
  void givenStandaloneAssignments_whenGroupCreated_thenLeadAssigneeWins(
      String rule, UUID leadAssignee, UUID memberAssignee, UUID expected) {
    UUID lead = createApplication(true);
    UUID member = createApplication(true);
    if (leadAssignee != null) {
      assign(lead, leadAssignee, assignmentToken(lead, 0));
    }
    if (memberAssignee != null) {
      assign(member, memberAssignee, assignmentToken(member, 0));
    }

    link(member, lead);
    UUID groupId = awaitGroup(lead);
    long version = expected == null ? 0 : 1;

    awaitWorkListRow(lead, expected, groupId, version);
    awaitWorkListRow(member, expected, groupId, version);
    assertThat(countEvents(groupId, LinkedGroupAssignedEvent.class))
        .isEqualTo(expected == null ? 0 : 1);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("addRules")
  void givenGroupAndMemberAssignments_whenAdded_thenGroupAssigneeWins(
      String rule,
      UUID groupAssignee,
      UUID memberAssignee,
      UUID expected,
      boolean groupEventOnAdd) {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    if (groupAssignee != null) {
      assign(a, groupAssignee, assignmentToken(groupId, 0));
    }
    UUID newMember = createApplication(true);
    if (memberAssignee != null) {
      assign(newMember, memberAssignee, assignmentToken(newMember, 0));
    }

    link(newMember, a, linkedGroupToken(groupId, 0));
    long version = (groupAssignee == null ? 0 : 1) + (groupEventOnAdd ? 1 : 0);
    for (UUID member : List.of(a, b, newMember)) {
      awaitWorkListRow(member, expected, groupId, version);
    }
    assertThat(countEvents(groupId, LinkedGroupAssignedEvent.class)).isEqualTo(version);
  }

  @Test
  void givenAssignedGroup_whenMemberBecomesReady_thenInheritsAssignment() {
    UUID a = createApplication(true);
    UUID b = createApplication(false);
    link(b, a);
    UUID groupId = awaitGroup(a);
    assign(a, C1, assignmentToken(groupId, 0));

    assertThat(markReady(b).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    awaitWorkListRow(b, C1, groupId, 1);
    assertThat(routeKind(b)).isEqualTo("LINKED_GROUP");
    assertThat(countEvents(groupId, LinkedGroupMemberAssignmentInheritedEvent.class)).isEqualTo(1);
    awaitHistoryCount(b, "ASSIGN_APPLICATION_TO_CASEWORKER", 1);
  }

  @Test
  void givenUnassignedGroup_whenMemberBecomesReady_thenUnassignedInGroup() {
    UUID a = createApplication(true);
    UUID b = createApplication(false);
    link(b, a);
    UUID groupId = awaitGroup(a);
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        jdbcTemplate.queryForList(
                            "SELECT group_id, active FROM axon.work_list_assignment_application "
                                + "WHERE application_id = ?",
                            b))
                    .singleElement()
                    .satisfies(
                        row -> {
                          assertThat(row.get("group_id")).isEqualTo(groupId);
                          assertThat(row.get("active")).isEqualTo(false);
                        }));

    ResponseEntity<String> ready = markReady(b);
    assertThat(ready.getStatusCode()).as(ready.getBody()).isEqualTo(HttpStatus.NO_CONTENT);
    awaitWorkItemRoute(b, "LINKED_GROUP");
    assertThat(countEvents(b, ApplicationReadyForManualAssessmentEvent.class)).isEqualTo(1);
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        jdbcTemplate.queryForObject(
                            "SELECT application_version FROM axon.application_current_state "
                                + "WHERE application_id = ?",
                            Long.class,
                            b))
                    .isEqualTo(1L));
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT application_id, group_id, active, caseworker_id, assignment_version "
                    + "FROM axon.work_list_assignment_application WHERE application_id = ?",
                b))
        .as("work-list projection's linked-group assignment tracker row")
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("group_id")).isEqualTo(groupId);
              assertThat(row.get("active")).isEqualTo(true);
            });

    awaitWorkListRow(b, null, groupId, 0);
    assertThat(countEvents(groupId, LinkedGroupMemberAssignmentInheritedEvent.class)).isZero();
  }

  @Test
  void givenAssignedGroup_whenMemberAutoGranted_thenNeverShowsGroupCaseworker() {
    UUID a = createApplication(true);
    UUID b = createApplication(false);
    link(b, a);
    UUID groupId = awaitGroup(a);
    assign(a, C1, assignmentToken(groupId, 0));

    assertThat(autoGrant(b).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    awaitShownCaseworker(b, null);
    awaitNoWorkListRow(b);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.work_item_route WHERE work_item_id = ?",
                Integer.class,
                b))
        .isZero();
  }

  @Test
  void givenAssignedGroup_whenAssigneeDecidesMember_thenDecisionSucceeds() {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    assign(a, C1, assignmentToken(groupId, 0));

    assertThat(decide(b, C1).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    awaitNoWorkListRow(b);
    awaitShownCaseworker(b, C1);
    awaitWorkListRow(a, C1, groupId, 1);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedDecisions")
  void givenGroupedMember_whenDecisionRejected_thenConflict(
      String scenario, UUID groupAssignee, UUID decider, String reason) {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    if (groupAssignee != null) {
      assign(a, groupAssignee, assignmentToken(groupId, 0));
    }

    ResponseEntity<String> response = decide(b, decider);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).contains(reason);
    assertThat(countEvents(b, ApplicationDecisionMadeEvent.class)).isZero();
  }

  @Test
  void givenMemberDecidedWhileGrouped_whenUnassignedAndLeft_thenKeepsCaseworker() {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    UUID c = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    link(c, a, linkedGroupToken(groupId, 0));
    assign(a, C1, assignmentToken(groupId, 0));
    assertThat(decide(b, C1).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(unassign(a, assignmentToken(groupId, 1)).getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListRow(a, null, groupId, 2);
    awaitWorkListRow(c, null, groupId, 2);
    awaitShownCaseworker(b, C1);
    assertThat(unlink(b, linkedGroupToken(groupId, 1)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitShownCaseworker(b, C1);
    awaitHistoryCount(b, "APPLICATION_GROUP_LEFT", 1);
    assertThat(countHistory(b, "UNASSIGN_APPLICATION_TO_CASEWORKER")).isZero();
  }

  @Test
  void givenAssignedGroup_whenActiveMemberUnlinked_thenOwnsAssignmentDirectly() {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    UUID c = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    link(c, a, linkedGroupToken(groupId, 0));
    assign(a, C1, assignmentToken(groupId, 0));

    assertThat(unlink(c, linkedGroupToken(groupId, 1)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitWorkListRow(c, C1, c, 2);
    assertThat(routeKind(c)).isEqualTo("STANDALONE");
    ResponseEntity<String> oldOwner = unassign(c, assignmentToken(groupId, 1));
    assertThat(oldOwner.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(oldOwner.getBody()).contains("the assignment version is stale");
    assertThat(unassign(c, assignmentToken(c, 2)).getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListRow(c, null, c, 3);
    awaitWorkListRow(a, C1, groupId, 1);
    awaitWorkListRow(b, C1, groupId, 1);
  }

  @Test
  void givenAssignedGroup_whenNotReadyMemberUnlinked_thenUnassignedWhenReady() {
    UUID a = createApplication(true);
    UUID b = createApplication(false);
    UUID c = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    link(c, a, linkedGroupToken(groupId, 0));
    assign(a, C1, assignmentToken(groupId, 0));

    assertThat(unlink(b, linkedGroupToken(groupId, 1)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(markReady(b).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    awaitWorkListRow(b, null, b, 2);
    assertThat(assign(b, C2, assignmentToken(b, 2)).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void givenAssignedTwoMemberGroup_whenDissolved_thenBothKeepCaseworker() {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    assign(a, C1, assignmentToken(groupId, 0));

    assertThat(unlink(b, linkedGroupToken(groupId, 0)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    awaitWorkListRow(a, C1, a, 2);
    awaitWorkListRow(b, C1, b, 2);
    assertThat(countEvents(a, ApplicationAssignmentReclaimedFromGroupEvent.class)).isEqualTo(1);
    assertThat(countEvents(b, ApplicationAssignmentReclaimedFromGroupEvent.class)).isEqualTo(1);
  }

  @Test
  void givenAssignedGroup_whenLeadChanged_thenAssignmentUnchanged() {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    link(b, a);
    UUID groupId = awaitGroup(a);
    assign(a, C1, assignmentToken(groupId, 0));

    assertThat(makeLead(b, linkedGroupToken(groupId, 0)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    awaitWorkListRow(a, C1, groupId, 1);
    awaitWorkListRow(b, C1, groupId, 1);
    assertThat(countEvents(groupId, LinkedGroupAssignedEvent.class)).isEqualTo(1);
    assertThat(countEvents(groupId, LinkedGroupUnassignedEvent.class)).isZero();
  }

  @Test
  void givenGroupedLead_whenPriorAuthorityAssigned_thenGroupUnaffected() throws Exception {
    UUID lead = createApplication(false);
    assertThat(autoGrant(lead).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    UUID member = createApplication(true);
    link(member, lead);
    UUID groupId = awaitGroup(member);
    assign(member, C1, assignmentToken(groupId, 0));
    UUID priorAuthority = createAndSubmitPriorAuthorityDraft(lead);

    assertThat(assign(priorAuthority, C2, assignmentToken(priorAuthority, 0)).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    awaitWorkListRow(priorAuthority, C2, priorAuthority, 1);
    awaitWorkListRow(member, C1, groupId, 1);
    assertThat(countEvents(groupId, LinkedGroupAssignedEvent.class)).isEqualTo(1);
  }

  @Test
  void givenGroupEventRejected_whenLinked_thenNoDelegationPersists() throws Exception {
    UUID a = createApplication(true);
    UUID b = createApplication(true);
    jdbcTemplate.execute(
        """
        CREATE OR REPLACE FUNCTION axon.reject_test_group_created_event()
        RETURNS trigger AS $$
        BEGIN
          IF NEW.payload_type LIKE '%LinkedApplicationGroupCreatedEvent' THEN
            RAISE EXCEPTION 'forced group creation failure';
          END IF;
          RETURN NEW;
        END;
        $$ LANGUAGE plpgsql
        """);
    jdbcTemplate.execute(
        """
        CREATE TRIGGER reject_test_group_created_event
        BEFORE INSERT ON axon.domain_event_entry
        FOR EACH ROW EXECUTE FUNCTION axon.reject_test_group_created_event()
        """);
    try {
      ResponseEntity<String> response = link(b, a);
      assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    } finally {
      jdbcTemplate.execute(
          "DROP TRIGGER IF EXISTS reject_test_group_created_event ON axon.domain_event_entry");
      jdbcTemplate.execute("DROP FUNCTION IF EXISTS axon.reject_test_group_created_event()");
    }

    assertThat(countDelegationEvents(a)).isZero();
    assertThat(countDelegationEvents(b)).isZero();
    awaitApplicationRoute(a, "STANDALONE");
    awaitApplicationRoute(b, "STANDALONE");
  }

  private static Stream<Arguments> creationRules() {
    return Stream.of(
        Arguments.of("AC1 lead assigned", C1, null, C1),
        Arguments.of("AC2 both assigned differently", C1, C2, C1),
        Arguments.of("AC3 only member assigned", null, C2, C2),
        Arguments.of("AC4 neither assigned", null, null, null),
        Arguments.of("AC4 both assigned identically", C1, C1, C1));
  }

  private static Stream<Arguments> addRules() {
    return Stream.of(
        Arguments.of("AC1 group assigned", C1, null, C1, false),
        Arguments.of("AC2 group and member assigned differently", C1, C2, C1, false),
        Arguments.of("AC3 only joining member assigned", null, C2, C2, true),
        Arguments.of("AC4 neither assigned", null, null, null, false),
        Arguments.of("AC4 assigned identically", C1, C1, C1, false));
  }

  private static Stream<Arguments> rejectedDecisions() {
    return Stream.of(
        Arguments.of("unassigned group", null, C1, "it is unassigned"),
        Arguments.of(
            "other caseworker", C1, C2, "the supplied caseworker is not its current assignee"));
  }

  private UUID createApplication(boolean ready) {
    UUID id = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    proceedingIds.put(id, proceedingId);
    var request = validCreateApplicationRequest(id, proceedingId);
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    Map<?, ?> originalProvider = (Map<?, ?>) content.get("provider");
    Map<String, Object> provider = new HashMap<>();
    originalProvider.forEach((key, value) -> provider.put(key.toString(), value));
    provider.put("officeCode", "1A001B");
    content.put("provider", provider);
    request.setApplicationContent(content);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            url("/api/v0/applications"), new HttpEntity<>(request, headers()), String.class);
    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    if (ready) {
      assertThat(markReady(id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
    return id;
  }

  private ResponseEntity<String> link(UUID source, UUID target) {
    return link(source, target, null);
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

  private ResponseEntity<String> unlink(UUID id, String token) {
    return groupMutation(id, "unlink", token);
  }

  private ResponseEntity<String> makeLead(UUID id, String token) {
    return groupMutation(id, "make-lead", token);
  }

  private ResponseEntity<String> groupMutation(UUID id, String operation, String token) {
    return restTemplate.exchange(
        url("/api/v0/applications/" + id + "/" + operation),
        HttpMethod.POST,
        new HttpEntity<>(new LinkedGroupChangeRequest(token), headers()),
        String.class);
  }

  private String linkedGroupToken(UUID groupId, long version) {
    return VersionToken.linkedGroup(groupId, version).encode();
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

  private String assignmentToken(UUID ownerId, long version) {
    return VersionToken.workItemAssignment(ownerId, version).encode();
  }

  private ResponseEntity<String> assign(UUID item, UUID caseworker, String token) {
    return restTemplate.exchange(
        url("/api/v0/work-list/" + item + "/assign"),
        HttpMethod.POST,
        new HttpEntity<>(new WorkListAssignRequest(token), headersFor(caseworker)),
        String.class);
  }

  private ResponseEntity<String> unassign(UUID item, String token) {
    return restTemplate.exchange(
        url("/api/v0/work-list/" + item + "/unassign"),
        HttpMethod.POST,
        new HttpEntity<>(new WorkListUnassignRequest(token), headers()),
        String.class);
  }

  private ResponseEntity<String> markReady(UUID id) {
    return restTemplate.exchange(
        url("/api/v0/applications/" + id + "/auto-grant-outcome"),
        HttpMethod.PATCH,
        new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL), headers()),
        String.class);
  }

  private ResponseEntity<String> autoGrant(UUID id) {
    return restTemplate.exchange(
        url("/api/v0/applications/" + id + "/auto-grant-outcome"),
        HttpMethod.PATCH,
        new HttpEntity<>(
            new AutoGrantedOutcomeRequest(
                AutoGrantOutcome.AUTOGRANTED, Map.of("certificateNumber", "PA-CERT-001")),
            headers()),
        String.class);
  }

  private ResponseEntity<String> decide(UUID id, UUID caseworker) {
    MakeDecisionRequest request =
        MakeDecisionRequest.builder()
            .applicationVersion(1L)
            .overallDecision(DecisionStatus.REFUSED)
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .proceedings(
                List.of(
                    MakeDecisionProceedingRequest.builder()
                        .proceedingId(proceedingIds.get(id))
                        .meritsDecision(
                            MeritsDecisionDetailsRequest.builder()
                                .decision(MeritsDecisionStatus.REFUSED)
                                .reason("Insufficient evidence")
                                .justification("The evidence did not meet the test")
                                .build())
                        .build()))
            .build();
    return restTemplate.exchange(
        url("/api/v0/applications/" + id + "/decision"),
        HttpMethod.PATCH,
        new HttpEntity<>(request, headersFor(caseworker)),
        String.class);
  }

  private void awaitWorkListRow(UUID item, UUID assignee, UUID owner, long version) {
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
              assertThat(row.get("assignment_boundary_type"))
                  .isEqualTo(owner.equals(item) ? "DIRECT" : "LINKED_GROUP");
              assertThat(row.get("assignment_boundary_id")).isEqualTo(owner);
              assertThat(row.get("group_id")).isEqualTo(owner.equals(item) ? null : owner);
            });
  }

  private void awaitNoWorkListRow(UUID item) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM axon.work_list_item WHERE item_id = ?",
                            Integer.class,
                            item))
                    .isZero());
  }

  private void awaitShownCaseworker(UUID id, UUID caseworker) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(
                      jdbcTemplate.queryForObject(
                          "SELECT caseworker_id FROM axon.application_current_state "
                              + "WHERE application_id = ?",
                          UUID.class,
                          id))
                  .isEqualTo(caseworker);
              assertThat(
                      jdbcTemplate.queryForObject(
                          "SELECT caseworker_id FROM axon.application_list_index "
                              + "WHERE application_id = ?",
                          UUID.class,
                          id))
                  .isEqualTo(caseworker);
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

  private long countHistory(UUID id, String eventType) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.application_history WHERE application_id = ? AND event_type = ?",
        Long.class,
        id,
        eventType);
  }

  private void awaitHistoryCount(UUID id, String eventType, long expectedCount) {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(countHistory(id, eventType)).isEqualTo(expectedCount));
  }

  private String routeKind(UUID id) {
    return jdbcTemplate.queryForObject(
        "SELECT route_kind FROM axon.work_item_route WHERE work_item_id = ?", String.class, id);
  }

  private void awaitWorkItemRoute(UUID id, String expected) {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(routeKind(id)).isEqualTo(expected));
  }

  private long countDelegationEvents(UUID id) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ? "
            + "AND payload_type LIKE '%ApplicationAssignmentDelegatedToGroupEvent'",
        Long.class, id.toString());
  }

  private void awaitApplicationRoute(UUID id, String expected) {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        jdbcTemplate.queryForObject(
                            "SELECT route_kind FROM axon.application_group_route "
                                + "WHERE application_id = ?",
                            String.class,
                            id))
                    .isEqualTo(expected));
  }

  private UUID createAndSubmitPriorAuthorityDraft(UUID applicationId) throws Exception {
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
            .justification("Interpreter costs for proceedings")
            .disbursementDetails(
                DisbursementDetails.builder()
                    .disbursementPurpose("Court interpreter")
                    .disbursementAmount(BigDecimal.valueOf(150))
                    .build())
            .build();
    ResponseEntity<String> draft =
        restTemplate.postForEntity(
            url("/api/v0/prior-authorities"), new HttpEntity<>(request, headers()), String.class);
    assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID id =
        objectMapper
            .readValue(draft.getBody(), SavePriorAuthorityDraftResponse.class)
            .getPriorAuthorityId();
    ResponseEntity<String> submitted =
        restTemplate.postForEntity(
            url("/api/v0/prior-authorities/" + id + "/submit"),
            new HttpEntity<>(request, headers()),
            String.class);
    assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
    return id;
  }
}
