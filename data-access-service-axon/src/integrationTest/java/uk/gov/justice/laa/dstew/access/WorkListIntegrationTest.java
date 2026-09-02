package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.Map;
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
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListItemType;
import uk.gov.justice.laa.dstew.access.model.WorkListResponse;
import uk.gov.justice.laa.dstew.access.model.WorkListUnassignRequest;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/** Full HTTP/Postgres/Axon integration tests for delivered work-list behaviour. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkListIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void
      givenManualApplication_whenAssignedThenUnassigned_thenItsWorkListViewsAndConflictsAreConsistent() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    createManualApplication(applicationId);
    jdbcTemplate.update(
        "INSERT INTO axon.caseworkers (id, username) VALUES (?, ?)",
        caseworkerId,
        "caseworker@example.com");

    awaitWorkListContains("", applicationId, null, 0L);

    ResponseEntity<Void> assigned =
        restTemplate.exchange(
            assignmentUrl(applicationId, "assign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(caseworkerId, 0L), headers()),
            Void.class);
    assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListContains("?assignedTo=" + caseworkerId, applicationId, caseworkerId, 1L);
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(getWorkList("").getItems())
                    .extracting(item -> item.getItemId())
                    .doesNotContain(applicationId));

    ResponseEntity<Void> duplicate =
        restTemplate.exchange(
            assignmentUrl(applicationId, "assign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(caseworkerId, 0L), headers()),
            Void.class);
    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    ResponseEntity<Void> unassigned =
        restTemplate.exchange(
            assignmentUrl(applicationId, "unassign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListUnassignRequest(1L), headers()),
            Void.class);
    assertThat(unassigned.getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListContains("", applicationId, null, 2L);
  }

  @Test
  void
      givenManualApplicationAssignedToCaseworker_whenDecided_thenItIsRemovedFromTheWorkQueue()
          throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    UUID caseworkerId = createCaseworker("decider@example.com");
    ResponseEntity<Void> created =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(validCreateApplicationRequest(applicationId, proceedingId), headers()),
            Void.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

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
    awaitWorkListContains("", applicationId, null, 0L);

    assertThat(assign(applicationId, caseworkerId).getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListContains("?assignedTo=" + caseworkerId, applicationId, caseworkerId, 1L);

    MakeDecisionRequest decision =
        MakeDecisionRequest.builder()
            .expectedApplicationVersion(2L)
            .caseworkerId(caseworkerId)
            .overallDecision(DecisionStatus.REFUSED)
            .eventHistory(EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .proceedings(
                java.util.List.of(
                    MakeDecisionProceedingRequest.builder()
                        .proceedingId(proceedingId)
                        .meritsDecision(
                            MeritsDecisionDetailsRequest.builder()
                                .decision(MeritsDecisionStatus.REFUSED)
                                .reason("Insufficient evidence")
                                .justification("The evidence did not meet the test")
                                .build())
                        .build()))
            .build();
    ResponseEntity<Void> decided =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications/" + applicationId + "/decision",
            HttpMethod.PATCH,
            new HttpEntity<>(decision, headers()),
            Void.class);
    assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(getWorkList("").getItems())
                  .extracting(item -> item.getItemId())
                  .doesNotContain(applicationId);
              assertThat(getWorkList("?assignedTo=" + caseworkerId).getItems())
                  .extracting(item -> item.getItemId())
                  .doesNotContain(applicationId);
            });
  }

  @Test
  void givenManualApplicationAndPriorAuthority_whenUnassigned_thenBothAppearInOpenApplications()
      throws Exception {
    UUID manualApplicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    createManualApplication(manualApplicationId);
    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createPriorAuthority(parentApplicationId);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              WorkListResponse openApplications = getWorkList("");
              assertThat(openApplications.getItems())
                  .filteredOn(item -> item.getItemId().equals(manualApplicationId))
                  .singleElement()
                  .satisfies(
                      item -> {
                        assertThat(item.getItemType()).isEqualTo(WorkListItemType.APPLICATION);
                        assertThat(item.getParentApplicationId()).isNull();
                        assertThat(item.getAssignedTo()).isNull();
                      });
              assertThat(openApplications.getItems())
                  .filteredOn(item -> item.getItemId().equals(priorAuthorityId))
                  .singleElement()
                  .satisfies(
                      item -> {
                        assertThat(item.getItemType()).isEqualTo(WorkListItemType.PRIOR_AUTHORITY);
                        assertThat(item.getParentApplicationId()).isEqualTo(parentApplicationId);
                        assertThat(item.getAssignedTo()).isNull();
                      });
            });
  }

  @Test
  void givenUnassignedWorkQueueItemsWithDifferentSubmissionTimes_whenListed_thenOldestIsFirst()
      throws Exception {
    UUID manualApplicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    createManualApplication(manualApplicationId);
    awaitWorkListContains("", manualApplicationId, null, 0L);

    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createPriorAuthority(parentApplicationId);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(getWorkList("").getItems())
                    .filteredOn(
                        item ->
                            item.getItemId().equals(manualApplicationId)
                                || item.getItemId().equals(priorAuthorityId))
                    .extracting(item -> item.getItemId())
                    .containsExactly(manualApplicationId, priorAuthorityId));
  }

  @Test
  void
      givenTwoPriorAuthoritiesUnderOneApplication_whenOneIsClaimed_thenWorkQueueViewsKeepThemIndependent()
          throws Exception {
    UUID parentApplicationId = UUID.randomUUID();
    UUID claimantId = createCaseworker("claimant@example.com");
    UUID otherCaseworkerId = createCaseworker("other@example.com");
    createGrantedApplication(parentApplicationId);
    UUID claimedPriorAuthorityId = createPriorAuthority(parentApplicationId);
    UUID availablePriorAuthorityId = createPriorAuthority(parentApplicationId);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              WorkListResponse openApplications = getWorkList("");
              assertThat(openApplications.getItems())
                  .filteredOn(item -> item.getItemId().equals(claimedPriorAuthorityId))
                  .singleElement()
                  .satisfies(
                      item -> {
                        assertThat(item.getItemType()).isEqualTo(WorkListItemType.PRIOR_AUTHORITY);
                        assertThat(item.getParentApplicationId()).isEqualTo(parentApplicationId);
                        assertThat(item.getAssignedTo()).isNull();
                      });
              assertThat(openApplications.getItems())
                  .extracting(item -> item.getItemId())
                  .contains(availablePriorAuthorityId);
            });

    ResponseEntity<Void> assigned =
        restTemplate.exchange(
            assignmentUrl(claimedPriorAuthorityId, "assign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(claimantId, 0L), headers()),
            Void.class);
    assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              WorkListResponse openApplications = getWorkList("");
              assertThat(openApplications.getItems())
                  .extracting(item -> item.getItemId())
                  .contains(availablePriorAuthorityId)
                  .doesNotContain(claimedPriorAuthorityId);

              WorkListResponse claimantQueue = getWorkList("?assignedTo=" + claimantId);
              assertThat(claimantQueue.getItems())
                  .filteredOn(item -> item.getItemId().equals(claimedPriorAuthorityId))
                  .singleElement()
                  .satisfies(
                      item -> {
                        assertThat(item.getAssignedTo()).isEqualTo(claimantId);
                        assertThat(item.getParentApplicationId()).isEqualTo(parentApplicationId);
                      });
              assertThat(claimantQueue.getItems())
                  .extracting(item -> item.getItemId())
                  .doesNotContain(availablePriorAuthorityId);

              WorkListResponse otherCaseworkerQueue =
                  getWorkList("?assignedTo=" + otherCaseworkerId);
              assertThat(otherCaseworkerQueue.getItems())
                  .extracting(item -> item.getItemId())
                  .doesNotContain(claimedPriorAuthorityId);
            });
  }

  @Test
  void
      givenMultiplePriorAuthoritiesAssignedToOneCaseworker_whenPersonalQueueIsViewed_thenAllAreReturned()
          throws Exception {
    UUID parentApplicationId = UUID.randomUUID();
    UUID caseworkerId = createCaseworker("caseworker@example.com");
    createGrantedApplication(parentApplicationId);
    UUID firstPriorAuthorityId = createPriorAuthority(parentApplicationId);
    UUID secondPriorAuthorityId = createPriorAuthority(parentApplicationId);

    assertThat(assign(firstPriorAuthorityId, caseworkerId).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(assign(secondPriorAuthorityId, caseworkerId).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(getWorkList("?assignedTo=" + caseworkerId).getItems())
                    .filteredOn(
                        item ->
                            item.getItemId().equals(firstPriorAuthorityId)
                                || item.getItemId().equals(secondPriorAuthorityId))
                    .allSatisfy(
                        item -> {
                          assertThat(item.getItemType())
                              .isEqualTo(WorkListItemType.PRIOR_AUTHORITY);
                          assertThat(item.getParentApplicationId()).isEqualTo(parentApplicationId);
                          assertThat(item.getAssignedTo()).isEqualTo(caseworkerId);
                        })
                    .extracting(item -> item.getItemId())
                    .containsExactlyInAnyOrder(firstPriorAuthorityId, secondPriorAuthorityId));
  }

  @Test
  void
      givenInitialApplicationAndPriorAuthorityAssignedToOneCaseworker_whenPersonalQueueIsViewed_thenBothAreReturned()
          throws Exception {
    UUID manualApplicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    UUID caseworkerId = createCaseworker("caseworker@example.com");
    createManualApplication(manualApplicationId);
    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createPriorAuthority(parentApplicationId);

    assertThat(assign(manualApplicationId, caseworkerId).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(assign(priorAuthorityId, caseworkerId).getStatusCode()).isEqualTo(HttpStatus.OK);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              WorkListResponse personalQueue = getWorkList("?assignedTo=" + caseworkerId);
              assertThat(personalQueue.getItems())
                  .filteredOn(
                      item ->
                          item.getItemId().equals(manualApplicationId)
                              || item.getItemId().equals(priorAuthorityId))
                  .satisfiesExactlyInAnyOrder(
                      item -> {
                        assertThat(item.getItemId()).isEqualTo(manualApplicationId);
                        assertThat(item.getItemType()).isEqualTo(WorkListItemType.APPLICATION);
                        assertThat(item.getParentApplicationId()).isNull();
                        assertThat(item.getAssignedTo()).isEqualTo(caseworkerId);
                      },
                      item -> {
                        assertThat(item.getItemId()).isEqualTo(priorAuthorityId);
                        assertThat(item.getItemType()).isEqualTo(WorkListItemType.PRIOR_AUTHORITY);
                        assertThat(item.getParentApplicationId()).isEqualTo(parentApplicationId);
                        assertThat(item.getAssignedTo()).isEqualTo(caseworkerId);
                      });
            });
  }

  @Test
  void
      givenInitialApplicationAndPriorAuthorityAssignedToDifferentCaseworkers_whenPersonalQueuesAreViewed_thenEachExcludesTheOthersWork()
          throws Exception {
    UUID manualApplicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    UUID initialApplicationCaseworkerId = createCaseworker("initial@example.com");
    UUID priorAuthorityCaseworkerId = createCaseworker("prior-authority@example.com");
    createManualApplication(manualApplicationId);
    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createPriorAuthority(parentApplicationId);

    assertThat(assign(manualApplicationId, initialApplicationCaseworkerId).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(assign(priorAuthorityId, priorAuthorityCaseworkerId).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(getWorkList("?assignedTo=" + initialApplicationCaseworkerId).getItems())
                  .extracting(item -> item.getItemId())
                  .contains(manualApplicationId)
                  .doesNotContain(priorAuthorityId);
              assertThat(getWorkList("?assignedTo=" + priorAuthorityCaseworkerId).getItems())
                  .extracting(item -> item.getItemId())
                  .contains(priorAuthorityId)
                  .doesNotContain(manualApplicationId);
            });
  }

  private void createManualApplication(UUID applicationId) {
    ResponseEntity<Void> created =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

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
  }

  private UUID createCaseworker(String username) {
    UUID caseworkerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO axon.caseworkers (id, username) VALUES (?, ?)", caseworkerId, username);
    return caseworkerId;
  }

  private void createGrantedApplication(UUID applicationId) {
    ResponseEntity<Void> created =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    markApplicationAutoGranted(applicationId);
  }

  private void markApplicationAutoGranted(UUID applicationId) {
    ResponseEntity<Void> granted =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications/"
                + applicationId
                + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(
                new AutoGrantedOutcomeRequest(
                    AutoGrantOutcome.AUTOGRANTED, Map.of("certificateNumber", "PA-CERT-001")),
                headers()),
            Void.class);
    assertThat(granted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private Map<String, Object> parentState(UUID applicationId) {
    return jdbcTemplate.queryForMap(
        """
        SELECT caseworker_id, application_data_version, application_version
        FROM axon.application_current_state
        WHERE application_id = ?
        """,
        applicationId);
  }

  private UUID createPriorAuthority(UUID applicationId) throws Exception {
    CreatePriorAuthorityRequest request =
        CreatePriorAuthorityRequest.builder()
            .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
            .justification("Interpreter costs for proceedings")
            .disbursementDetails(
                DisbursementDetails.builder()
                    .disbursementPurpose("Court interpreter")
                    .disbursementAmount(150.0)
                    .build())
            .build();
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "http://localhost:"
                + port
                + "/api/v0/applications/"
                + applicationId
                + "/prior-authority",
            new HttpEntity<>(request, headers()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper
        .readValue(response.getBody(), CreatePriorAuthorityResponse.class)
        .getSubmissionId();
  }

  private void awaitWorkListContains(
      String query, UUID applicationId, UUID expectedAssignee, long expectedAssignmentVersion) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              ResponseEntity<WorkListResponse> response =
                  restTemplate.exchange(
                      "http://localhost:" + port + "/api/v0/work-list" + query,
                      HttpMethod.GET,
                      new HttpEntity<>(headers()),
                      WorkListResponse.class);
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(response.getBody()).isNotNull();
              assertThat(response.getBody().getItems())
                  .filteredOn(item -> item.getItemId().equals(applicationId))
                  .singleElement()
                  .satisfies(
                      item -> {
                        assertThat(item.getItemType()).isEqualTo(WorkListItemType.APPLICATION);
                        assertThat(item.getAssignedTo()).isEqualTo(expectedAssignee);
                        assertThat(item.getAssignmentVersion())
                            .isEqualTo(expectedAssignmentVersion);
                      });
            });
  }

  private WorkListResponse getWorkList(String query) {
    ResponseEntity<WorkListResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/work-list" + query,
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            WorkListResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    return response.getBody();
  }

  private String assignmentUrl(UUID itemId, String operation) {
    return "http://localhost:" + port + "/api/v0/work-list/" + itemId + "/" + operation;
  }

  private ResponseEntity<Void> assign(UUID itemId, UUID caseworkerId) {
    return restTemplate.exchange(
        assignmentUrl(itemId, "assign"),
        HttpMethod.POST,
        new HttpEntity<>(new WorkListAssignRequest(caseworkerId, 0L), headers()),
        Void.class);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}
