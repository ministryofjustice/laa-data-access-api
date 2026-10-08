package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
 * Full HTTP/Postgres/Axon integration tests proving that assigning one member of a linked
 * application group propagates the caseworker to every member of the group, and that the
 * propagation is atomic across the whole group.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GroupWorkItemAssignmentIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ApplicationGroupRouteRepository routeRepository;

  @Test
  void givenAnUnassignedLinkedGroup_whenOneMemberIsAssigned_thenEveryMemberIsAssigned() {
    List<UUID> memberIds = createGroup(3);

    assertThat(assign(memberIds.get(1), TestJwtDecoderConfig.CASEWORKER_ID).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    for (UUID memberId : memberIds) {
      awaitWorkListAssignee(memberId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
    }
  }

  @Test
  void givenFullyAssignedLinkedGroup_whenReassigned_thenEveryMemberIsReassigned() {
    List<UUID> memberIds = createGroup(3);
    assertThat(assign(memberIds.get(0), TestJwtDecoderConfig.CASEWORKER_ID).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    for (UUID memberId : memberIds) {
      awaitWorkListAssignee(memberId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
    }

    assertThat(assign(memberIds.get(2), TestJwtDecoderConfig.OTHER_CASEWORKER_ID).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    for (UUID memberId : memberIds) {
      awaitWorkListAssignee(memberId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 2L);
    }
  }

  @Test
  void givenGroupDispatchFailsPartway_whenAssigning_thenNoMemberIsAssigned() {
    List<UUID> memberIds = createGroup(3);
    List<UUID> dispatchOrder = memberIds.stream().sorted(Comparator.naturalOrder()).toList();
    UUID poisonMemberId = dispatchOrder.getLast();

    try {
      jdbcTemplate.execute(
          """
          CREATE OR REPLACE FUNCTION axon.reject_test_group_assignment_event()
          RETURNS trigger AS $$
          BEGIN
            IF NEW.aggregate_identifier = '%s' THEN
              RAISE EXCEPTION 'forced group assignment failure';
            END IF;
            RETURN NEW;
          END;
          $$ LANGUAGE plpgsql
          """
              .formatted(poisonMemberId));
      jdbcTemplate.execute(
          """
          CREATE TRIGGER reject_test_group_assignment_event
          BEFORE INSERT ON axon.domain_event_entry
          FOR EACH ROW EXECUTE FUNCTION axon.reject_test_group_assignment_event()
          """);

      ResponseEntity<String> response =
          assign(dispatchOrder.getFirst(), TestJwtDecoderConfig.CASEWORKER_ID);
      assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    } finally {
      jdbcTemplate.execute(
          "DROP TRIGGER IF EXISTS reject_test_group_assignment_event ON axon.domain_event_entry");
      jdbcTemplate.execute("DROP FUNCTION IF EXISTS axon.reject_test_group_assignment_event()");
    }

    for (UUID memberId : memberIds) {
      assertThat(countDomainEvents(memberId, WorkItemAssigned.class.getName())).isZero();
    }
  }

  private ResponseEntity<String> assign(UUID itemId, UUID caseworkerId) {
    return restTemplate.exchange(
        "http://localhost:" + port + "/api/v0/work-list/" + itemId + "/assign",
        HttpMethod.POST,
        new HttpEntity<>(new WorkListAssignRequest(0L), headersFor(caseworkerId)),
        String.class);
  }

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

  private void awaitWorkListAssignee(
      UUID itemId, UUID expectedAssignee, long expectedAssignmentVersion) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              Map<String, Object> row =
                  jdbcTemplate.queryForMap(
                      "SELECT assignee_id, assignment_version FROM axon.work_list_item WHERE item_id = ?",
                      itemId);
              assertThat(row.get("assignee_id")).isEqualTo(expectedAssignee);
              assertThat(((Number) row.get("assignment_version")).longValue())
                  .isEqualTo(expectedAssignmentVersion);
            });
  }

  private List<UUID> createGroup(int memberCount) {
    String lastName = uniqueLastName();
    List<UUID> memberIds = new ArrayList<>();
    for (int index = 0; index < memberCount; index++) {
      memberIds.add(createApplication(lastName));
    }
    UUID leadId = memberIds.getFirst();
    assertThat(link(memberIds.get(1), leadId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    UUID groupId = awaitRoute(leadId, ApplicationGroupRouteKind.LINKED_GROUP);
    for (int index = 2; index < memberCount; index++) {
      assertThat(
              link(memberIds.get(index), leadId, token(groupId, (long) index - 2)).getStatusCode())
          .isEqualTo(HttpStatus.NO_CONTENT);
    }
    for (UUID memberId : memberIds) {
      awaitRoute(memberId, ApplicationGroupRouteKind.LINKED_GROUP);
    }
    return memberIds;
  }

  private UUID createApplication(String lastName) {
    UUID applicationId = UUID.randomUUID();
    var request = validCreateApplicationRequest(applicationId, UUID.randomUUID());
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    Map<?, ?> originalClient = (Map<?, ?>) content.get("client");
    Map<String, Object> client = new HashMap<>();
    originalClient.forEach((key, value) -> client.put(key.toString(), value));
    client.put("lastName", lastName);
    content.put("client", client);
    Map<?, ?> originalProvider = (Map<?, ?>) content.get("provider");
    Map<String, Object> provider = new HashMap<>();
    originalProvider.forEach((key, value) -> provider.put(key.toString(), value));
    provider.put("officeCode", "1A001B");
    content.put("provider", provider);
    request.setApplicationContent(content);

    ResponseEntity<Void> created =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(created.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);

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
    awaitRoute(applicationId, ApplicationGroupRouteKind.STANDALONE);
    return applicationId;
  }

  private ResponseEntity<Void> link(UUID sourceId, UUID targetId, String token) {
    var request = new ApplicationLinkRequest(targetId, ApplicationLinkType.FAMILY);
    request.setLinkedGroupVersion(token);
    return restTemplate.exchange(
        "/api/v0/applications/" + sourceId + "/link",
        HttpMethod.POST,
        new HttpEntity<>(request, headers()),
        Void.class);
  }

  private UUID awaitRoute(UUID applicationId, ApplicationGroupRouteKind expectedKind) {
    return await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> routeRepository.findById(applicationId).orElse(null),
            route -> route != null && route.getRouteKind() == expectedKind)
        .getGroupId();
  }

  private String token(UUID groupId, long version) {
    return VersionToken.linkedGroup(groupId, version).encode();
  }

  private String uniqueLastName() {
    return "GroupAssign" + UUID.randomUUID().toString().replace("-", "");
  }

  private HttpHeaders headers() {
    return headersFor(TestJwtDecoderConfig.CASEWORKER_ID);
  }

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
