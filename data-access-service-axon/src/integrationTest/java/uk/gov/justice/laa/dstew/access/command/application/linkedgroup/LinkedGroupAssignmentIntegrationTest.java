package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.DataAccessServiceAxonApplication;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.access.version.VersionToken;

/**
 * Full HTTP/Postgres/Axon integration tests proving that linked group assignment propagates
 * caseworker assignments correctly across group members.
 *
 * <p>Verifies that:
 *
 * <ul>
 *   <li>When one member of a linked group is assigned, the assignment propagates to all members
 *   <li>When a linked group member is reassigned, all members are reassigned atomically
 *   <li>Assignment operations maintain consistency across group members
 *   <li>The LinkedGroupAssignmentEventHandler correctly synchronizes assignments
 * </ul>
 */
@Testcontainers
@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("LinkedGroupAssignment Integration Tests")
class LinkedGroupAssignmentIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ApplicationGroupRouteRepository routeRepository;

  @Test
  @DisplayName("When one member is assigned, all members of the linked group are assigned")
  void givenUnassignedLinkedGroup_whenOneMemberIsAssigned_thenEveryMemberIsAssigned() {
    List<UUID> memberIds = createLinkedGroup(3);

    // Assign the second member
    ResponseEntity<Void> assignResponse =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/work-list/" + memberIds.get(1) + "/assign",
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(0L), headers()),
            Void.class);

    assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Verify all members are assigned to the same caseworker
    for (UUID memberId : memberIds) {
      awaitWorkListAssignee(memberId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
    }
  }

  @Test
  @DisplayName("When a member is reassigned, all members of the linked group are reassigned")
  void givenFullyAssignedLinkedGroup_whenReassigned_thenEveryMemberIsReassigned() {
    List<UUID> memberIds = createLinkedGroup(3);

    // Initial assignment
    restTemplate.exchange(
        "http://localhost:" + port + "/api/v0/work-list/" + memberIds.get(0) + "/assign",
        HttpMethod.POST,
        new HttpEntity<>(new WorkListAssignRequest(0L), headers()),
        Void.class);

    // Verify all members have version 1
    for (UUID memberId : memberIds) {
      awaitWorkListAssignee(memberId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
    }

    // Reassign to different caseworker
    restTemplate.exchange(
        "http://localhost:" + port + "/api/v0/work-list/" + memberIds.get(2) + "/assign",
        HttpMethod.POST,
        new HttpEntity<>(
            new WorkListAssignRequest(1L), headersFor(TestJwtDecoderConfig.OTHER_CASEWORKER_ID)),
        Void.class);

    // Verify all members are reassigned to new caseworker with version 2
    for (UUID memberId : memberIds) {
      awaitWorkListAssignee(memberId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 2L);
    }
  }

  @Test
  @DisplayName("Assignment events are recorded correctly for each group member")
  void givenGroupAssignment_whenCompleted_thenEveryMemberHasWorkItemAssignedEvent() {
    List<UUID> memberIds = createLinkedGroup(2);

    // Assign first member
    restTemplate.exchange(
        "http://localhost:" + port + "/api/v0/work-list/" + memberIds.get(0) + "/assign",
        HttpMethod.POST,
        new HttpEntity<>(new WorkListAssignRequest(0L), headers()),
        Void.class);

    // Verify both members have WorkItemAssigned events
    for (UUID memberId : memberIds) {
      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () -> {
                long eventCount = countDomainEvents(memberId, WorkItemAssigned.class.getName());
                assertThat(eventCount)
                    .as("WorkItemAssigned event should exist for member " + memberId)
                    .isGreaterThan(0);
              });
    }
  }

  /**
   * Creates a linked group with the specified number of members. The first application is the lead,
   * and subsequent applications are linked to it.
   */
  private List<UUID> createLinkedGroup(int memberCount) {
    String lastName = "LinkedGroupAssign" + UUID.randomUUID().toString().replace("-", "");
    List<UUID> memberIds = new ArrayList<>();

    // Create lead application
    UUID leadId = createApplication(lastName);
    memberIds.add(leadId);

    // Create and link member applications
    for (int index = 1; index < memberCount; index++) {
      UUID memberId = createApplication(lastName);
      memberIds.add(memberId);

      // Link member to lead
      String linkToken =
          index > 1
              ? token(awaitRoute(leadId, ApplicationGroupRouteKind.LINKED_GROUP), index - 2L)
              : null;
      var linkRequest = new ApplicationLinkRequest(leadId, ApplicationLinkType.FAMILY);
      linkRequest.setLinkedGroupVersion(linkToken);

      ResponseEntity<Void> linkResponse =
          restTemplate.exchange(
              "http://localhost:" + port + "/api/v0/applications/" + memberId + "/link",
              HttpMethod.POST,
              new HttpEntity<>(linkRequest, headers()),
              Void.class);

      assertThat(linkResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // Verify all members have the linked group route
    for (UUID memberId : memberIds) {
      awaitRoute(memberId, ApplicationGroupRouteKind.LINKED_GROUP);
    }

    return memberIds;
  }

  /** Creates a new application and sets it to MANUAL status (ready for assignment). */
  private UUID createApplication(String lastName) {
    UUID applicationId = UUID.randomUUID();
    var request = validCreateApplicationRequest(applicationId, UUID.randomUUID());

    // Update client last name for grouping
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    Map<?, ?> originalClient = (Map<?, ?>) content.get("client");
    Map<String, Object> client = new HashMap<>();
    originalClient.forEach((key, value) -> client.put(key.toString(), value));
    client.put("lastName", lastName);
    content.put("client", client);

    // Update provider office code
    Map<?, ?> originalProvider = (Map<?, ?>) content.get("provider");
    Map<String, Object> provider = new HashMap<>();
    originalProvider.forEach((key, value) -> provider.put(key.toString(), value));
    provider.put("officeCode", "1A001B");
    content.put("provider", provider);

    request.setApplicationContent(content);

    // Create application
    ResponseEntity<Void> created =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(created.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);

    // Set to MANUAL status
    ResponseEntity<Void> ready =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications/"
                + applicationId
                + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL), headers()),
            Void.class);
    assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Await standalone route before linking
    awaitRoute(applicationId, ApplicationGroupRouteKind.STANDALONE);

    return applicationId;
  }

  /** Waits for an application to have a route of the specified kind. */
  private UUID awaitRoute(UUID applicationId, ApplicationGroupRouteKind expectedKind) {
    return await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> routeRepository.findById(applicationId).orElse(null),
            route -> route != null && route.getRouteKind() == expectedKind)
        .getGroupId();
  }

  /** Waits for a work list item to be assigned to a specific caseworker with a specific version. */
  private void awaitWorkListAssignee(
      UUID itemId, UUID expectedAssignee, long expectedAssignmentVersion) {
    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              Map<String, Object> row =
                  jdbcTemplate.queryForMap(
                      "SELECT assignee_id, assignment_version FROM axon.work_list_item WHERE item_id = ?",
                      itemId);
              assertThat(row.get("assignee_id"))
                  .as("Assignee should match for item " + itemId)
                  .isEqualTo(expectedAssignee);
              assertThat(((Number) row.get("assignment_version")).longValue())
                  .as("Assignment version should match for item " + itemId)
                  .isEqualTo(expectedAssignmentVersion);
            });
  }

  /** Counts the domain events of a specific type for an aggregate. */
  private long countDomainEvents(UUID aggregateId, String payloadType) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM axon.domain_event_entry
            WHERE aggregate_identifier = ?
              AND payload_type = ?
            """,
            Long.class,
            aggregateId.toString(),
            payloadType));
  }

  /** Encodes a linked group version token. */
  private String token(UUID groupId, long version) {
    return VersionToken.linkedGroup(groupId, version).encode();
  }

  /** Creates HTTP headers with test JWT token for the default caseworker. */
  private HttpHeaders headers() {
    return headersFor(TestJwtDecoderConfig.CASEWORKER_ID);
  }

  /** Creates HTTP headers with test JWT token for a specific caseworker. */
  private HttpHeaders headersFor(UUID caseworkerId) {
    String bearerToken =
        caseworkerId.equals(TestJwtDecoderConfig.CASEWORKER_ID)
            ? TestJwtDecoderConfig.BEARER_TOKEN
            : TestJwtDecoderConfig.OTHER_BEARER_TOKEN;
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
