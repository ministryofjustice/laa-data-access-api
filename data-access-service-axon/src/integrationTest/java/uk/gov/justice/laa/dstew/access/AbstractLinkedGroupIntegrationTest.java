package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.LinkedGroupChangeRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListUnassignRequest;
import uk.gov.justice.laa.dstew.access.version.VersionToken;

/** Shared HTTP and database helpers for linked-group integration tests. */
public abstract class AbstractLinkedGroupIntegrationTest extends BaseIntegrationTest {

  @Autowired protected ApplicationGroupRouteRepository routeRepository;

  /** Creates an application and marks it ready for manual assessment. */
  protected UUID createApplication(String lastName) {
    var applicationId = createSubmittedApplication(lastName);
    assertThat(markReady(applicationId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    return applicationId;
  }

  /** Creates a submitted application without marking it ready. */
  protected UUID createSubmittedApplication(String lastName) {
    var applicationId = UUID.randomUUID();
    var request = validCreateApplicationRequest(applicationId, UUID.randomUUID());
    var content = new HashMap<>(request.getApplicationContent());

    var client = new HashMap<String, Object>();
    ((Map<?, ?>) content.get("client")).forEach((key, value) -> client.put(key.toString(), value));
    client.put("lastName", lastName);
    content.put("client", client);

    var provider = new HashMap<String, Object>();
    ((Map<?, ?>) content.get("provider"))
        .forEach((key, value) -> provider.put(key.toString(), value));
    provider.put("officeCode", "1A001B");
    content.put("provider", provider);
    request.setApplicationContent(content);

    var created =
        restTemplate.postForEntity(
            url("/api/v0/applications"), new HttpEntity<>(request, headers()), Void.class);
    assertThat(created.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    awaitRoute(applicationId, ApplicationGroupRouteKind.STANDALONE);
    return applicationId;
  }

  /** Marks an application ready for manual assessment. */
  protected ResponseEntity<Void> markReady(UUID applicationId) {
    return restTemplate.exchange(
        url("/api/v0/applications/" + applicationId + "/auto-grant-outcome"),
        HttpMethod.PATCH,
        new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL), headers()),
        Void.class);
  }

  /** Records an automatic grant for a submitted application. */
  protected void autoGrant(UUID applicationId) {
    var request =
        new AutoGrantedOutcomeRequest(
            AutoGrantOutcome.AUTOGRANTED, Map.of("certificateNumber", "AUTO-" + applicationId));
    var response =
        restTemplate.exchange(
            url("/api/v0/applications/" + applicationId + "/auto-grant-outcome"),
            HttpMethod.PATCH,
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  /** Links {@code sourceId} to {@code targetId}. */
  protected ResponseEntity<String> link(UUID sourceId, UUID targetId, @Nullable String token) {
    var request = new ApplicationLinkRequest(targetId, ApplicationLinkType.FAMILY);
    request.setLinkedGroupVersion(token);
    return restTemplate.exchange(
        url("/api/v0/applications/" + sourceId + "/link"),
        HttpMethod.POST,
        new HttpEntity<>(request, headers()),
        String.class);
  }

  /** Removes an application from its linked group at the expected group version. */
  protected ResponseEntity<String> unlink(UUID applicationId, String token) {
    return restTemplate.exchange(
        url("/api/v0/applications/" + applicationId + "/unlink"),
        HttpMethod.POST,
        new HttpEntity<>(new LinkedGroupChangeRequest(token), headers()),
        String.class);
  }

  /** Assigns an application work item to a caseworker at the expected version. */
  protected ResponseEntity<String> assign(UUID itemId, UUID caseworkerId, long version) {
    return restTemplate.exchange(
        url("/api/v0/work-list/" + itemId + "/assign"),
        HttpMethod.POST,
        new HttpEntity<>(new WorkListAssignRequest(version), headersFor(caseworkerId)),
        String.class);
  }

  /** Unassigns an application work item at the expected version. */
  protected ResponseEntity<String> unassign(UUID itemId, UUID caseworkerId, long version) {
    return restTemplate.exchange(
        url("/api/v0/work-list/" + itemId + "/unassign"),
        HttpMethod.POST,
        new HttpEntity<>(new WorkListUnassignRequest(version), headersFor(caseworkerId)),
        String.class);
  }

  /** Waits until the application has a route of the expected kind and returns its group ID. */
  protected @Nullable UUID awaitRoute(UUID applicationId, ApplicationGroupRouteKind expectedKind) {
    return await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> routeRepository.findById(applicationId).orElse(null),
            route -> route != null && route.getRouteKind() == expectedKind)
        .getGroupId();
  }

  /** Encodes a linked-group version token. */
  protected String groupToken(UUID groupId, long version) {
    return VersionToken.linkedGroup(groupId, version).encode();
  }

  /** Waits until the application's projected assignee and assignment version match. */
  protected void awaitWorkListAssignee(
      UUID itemId, @Nullable UUID expectedAssignee, long expectedAssignmentVersion) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              var row =
                  jdbcTemplate.queryForMap(
                      "SELECT assignee_id, assignment_version FROM axon.work_list_item WHERE item_id = ?",
                      itemId);
              assertThat(row.get("assignee_id")).isEqualTo(expectedAssignee);
              assertThat(((Number) row.get("assignment_version")).longValue())
                  .isEqualTo(expectedAssignmentVersion);
            });
  }

  /** Waits until the projected work item has the expected assignment boundary. */
  protected void awaitWorkListBoundary(
      UUID itemId, String expectedBoundaryType, UUID expectedBoundaryId) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              var row =
                  jdbcTemplate.queryForMap(
                      "SELECT assignment_boundary_type, assignment_boundary_id FROM axon.work_list_item WHERE item_id = ?",
                      itemId);
              assertThat(row.get("assignment_boundary_type")).isEqualTo(expectedBoundaryType);
              assertThat(row.get("assignment_boundary_id")).isEqualTo(expectedBoundaryId);
            });
  }

  /** Counts the domain events of {@code eventType} for an aggregate. */
  protected long countDomainEvents(UUID aggregateId, Class<?> eventType) {
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
            eventType.getName()));
  }

  /** Adds a trigger that rejects event appends for {@code aggregateId}. */
  protected void rejectEventsFor(UUID aggregateId) {
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
            .formatted(aggregateId));
    jdbcTemplate.execute(
        """
        CREATE TRIGGER reject_test_group_assignment_event
        BEFORE INSERT ON axon.domain_event_entry
        FOR EACH ROW EXECUTE FUNCTION axon.reject_test_group_assignment_event()
        """);
  }

  /** Removes the event-rejection trigger and function after each test. */
  @AfterEach
  protected void dropRejectTrigger() {
    jdbcTemplate.execute(
        "DROP TRIGGER IF EXISTS reject_test_group_assignment_event ON axon.domain_event_entry");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS axon.reject_test_group_assignment_event()");
  }

  /** Returns a unique surname for related application fixtures. */
  protected String uniqueLastName() {
    return "LinkedGroup" + UUID.randomUUID().toString().replace("-", "");
  }
}
