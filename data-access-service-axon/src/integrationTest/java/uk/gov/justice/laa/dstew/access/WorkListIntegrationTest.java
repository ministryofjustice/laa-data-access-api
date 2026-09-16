package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.List;
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
import uk.gov.justice.laa.dstew.access.model.BillingType;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.ExpertCosts;
import uk.gov.justice.laa.dstew.access.model.ExpertDetails;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListItem;
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
    UUID caseworkerId = createCaseworker("caseworker@example.com");
    createManualApplication(applicationId);
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
  void givenManualApplicationAssignedToCaseworker_whenDecided_thenItIsRemovedFromTheWorkQueue() {
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
            .applicationVersion(1L)
            .caseworkerId(caseworkerId)
            .overallDecision(DecisionStatus.REFUSED)
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .proceedings(
                List.of(
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
  void givenSubmittedApplication_whenAutoGrantOutcomeIsGranted_thenItDoesNotReachTheWorkList() {
    UUID applicationId = UUID.randomUUID();

    createGrantedApplication(applicationId);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(
                      jdbcTemplate.queryForObject(
                          "SELECT status FROM axon.application_current_state WHERE application_id = ?",
                          String.class,
                          applicationId))
                  .isEqualTo("APPLICATION_GRANTED");
              assertThat(getWorkList("").getItems())
                  .extracting(item -> item.getItemId())
                  .doesNotContain(applicationId);
            });
  }

  @Test
  void givenManualApplicationAndPriorAuthority_whenUnassigned_thenBothAppearInOpenApplications() {
    UUID manualApplicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    createManualApplication(manualApplicationId);
    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

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
                        assertThat(item.getPriorAuthorityType()).isNull();
                        assertThat(item.getExpertType()).isNull();
                      });
              assertThat(openApplications.getItems())
                  .filteredOn(item -> item.getItemId().equals(priorAuthorityId))
                  .singleElement()
                  .satisfies(
                      item -> {
                        assertThat(item.getItemType()).isEqualTo(WorkListItemType.PRIOR_AUTHORITY);
                        assertThat(item.getParentApplicationId()).isEqualTo(parentApplicationId);
                        assertThat(item.getAssignedTo()).isNull();
                        assertThat(item.getLaaReference()).isEqualTo("LAA-123");
                        assertThat(item.getCategoryOfLaw().getValue()).isEqualTo("FAMILY");
                        assertThat(item.getMatterTypes())
                            .extracting(matterType -> matterType.getValue())
                            .containsExactly("SPECIAL_CHILDREN_ACT");
                        assertThat(item.getPriorAuthorityType())
                            .isEqualTo(PriorAuthorityType.DISBURSEMENT);
                        assertThat(item.getExpertType()).isNull();
                      });
            });
  }

  @Test
  void givenExpertPriorAuthority_whenUnassigned_thenItsExpertTypeAppearsInTheWorkList() {
    UUID parentApplicationId = UUID.randomUUID();
    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId =
        createAndSubmitPriorAuthorityDraft(
            CreatePriorAuthorityDraftRequest.builder()
                .applicationId(parentApplicationId)
                .priorAuthorityType(PriorAuthorityType.EXPERT)
                .justification("Interpreter costs for proceedings")
                .expertDetails(
                    ExpertDetails.builder()
                        .expertType("Pathologist")
                        .expertFullName("Pathologist")
                        .expertPostcode("12345")
                        .expertCosts(
                            ExpertCosts.builder()
                                .billingType(BillingType.FIXED_RATE)
                                .totalAmount(100.0)
                                .costsSharedWithOtherParties(false)
                                .build())
                        .build())
                .build());

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(getWorkList("").getItems())
                    .filteredOn(item -> item.getItemId().equals(priorAuthorityId))
                    .singleElement()
                    .satisfies(
                        item -> {
                          assertThat(item.getPriorAuthorityType())
                              .isEqualTo(PriorAuthorityType.EXPERT);
                          assertThat(item.getExpertType()).isEqualTo("Pathologist");
                        }));
  }

  @Test
  void
      givenClaimableAssignedAndCompletedWork_whenOpenApplicationsAreViewed_thenOnlyClaimableWorkIsReturned() {
    UUID availableApplicationId = UUID.randomUUID();
    UUID assignedApplicationId = UUID.randomUUID();
    UUID completedApplicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    UUID caseworkerId = createCaseworker("claimable-work@example.com");
    createManualApplication(availableApplicationId);
    createManualApplication(assignedApplicationId);
    createGrantedApplication(completedApplicationId);
    createGrantedApplication(parentApplicationId);
    UUID availablePriorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);
    UUID assignedPriorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

    assertThat(assign(assignedApplicationId, caseworkerId).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(assign(assignedPriorAuthorityId, caseworkerId).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(getWorkList("").getItems())
                    .extracting(WorkListItem::getItemId)
                    .contains(availableApplicationId, availablePriorAuthorityId)
                    .doesNotContain(
                        assignedApplicationId,
                        assignedPriorAuthorityId,
                        completedApplicationId,
                        parentApplicationId));
  }

  @Test
  void givenUnassignedWorkQueueItemsWithDifferentSubmissionTimes_whenListed_thenOldestIsFirst() {
    UUID manualApplicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    createManualApplication(manualApplicationId);
    awaitWorkListContains("", manualApplicationId, null, 0L);

    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(getWorkList("").getItems())
                    .filteredOn(
                        item ->
                            item.getItemId().equals(manualApplicationId)
                                || item.getItemId().equals(priorAuthorityId))
                    .extracting(WorkListItem::getItemId)
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
    UUID claimedPriorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);
    UUID availablePriorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

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
                  .extracting(WorkListItem::getItemId)
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
                  .extracting(WorkListItem::getItemId)
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
                  .extracting(WorkListItem::getItemId)
                  .doesNotContain(availablePriorAuthorityId);

              WorkListResponse otherCaseworkerQueue =
                  getWorkList("?assignedTo=" + otherCaseworkerId);
              assertThat(otherCaseworkerQueue.getItems())
                  .extracting(WorkListItem::getItemId)
                  .doesNotContain(claimedPriorAuthorityId);
            });
  }

  @Test
  void
      givenMultiplePriorAuthoritiesAssignedToOneCaseworker_whenPersonalQueueIsViewed_thenAllAreReturned() {
    UUID parentApplicationId = UUID.randomUUID();
    UUID caseworkerId = createCaseworker("caseworker@example.com");
    createGrantedApplication(parentApplicationId);
    UUID firstPriorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);
    UUID secondPriorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

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
                    .extracting(WorkListItem::getItemId)
                    .containsExactlyInAnyOrder(firstPriorAuthorityId, secondPriorAuthorityId));
  }

  @Test
  void
      givenApplicationAndPriorAuthorityAssignedToOneCaseworker_whenPersonalQueueIsViewed_thenBothAreReturned() {
    UUID manualApplicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    UUID caseworkerId = createCaseworker("caseworker@example.com");
    createManualApplication(manualApplicationId);
    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

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
      givenApplicationAndPriorAuthorityAssignedToDifferentCaseworkers_whenPersonalQueuesAreViewed_thenEachExcludesTheOthersWork() {
    UUID manualApplicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    UUID initialApplicationCaseworkerId = createCaseworker("initial@example.com");
    UUID priorAuthorityCaseworkerId = createCaseworker("prior-authority@example.com");
    createManualApplication(manualApplicationId);
    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

    assertThat(assign(manualApplicationId, initialApplicationCaseworkerId).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(assign(priorAuthorityId, priorAuthorityCaseworkerId).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(getWorkList("?assignedTo=" + initialApplicationCaseworkerId).getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(manualApplicationId)
                  .doesNotContain(priorAuthorityId);
              assertThat(getWorkList("?assignedTo=" + priorAuthorityCaseworkerId).getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(priorAuthorityId)
                  .doesNotContain(manualApplicationId);
            });
  }

  @Test
  void givenMixedWorkList_whenFilteredAndPaged_thenPublicQueueContractsAreApplied() {
    UUID applicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    UUID caseworkerId = createCaseworker("filtered-work@example.com");
    createManualApplication(applicationId);
    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);
    assertThat(assign(applicationId, caseworkerId).getStatusCode()).isEqualTo(HttpStatus.OK);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(getWorkList("?itemType=APPLICATION").getItems())
                  .extracting(WorkListItem::getItemId)
                  .doesNotContain(priorAuthorityId);
              assertThat(getWorkList("?itemType=PRIOR_AUTHORITY").getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(priorAuthorityId)
                  .doesNotContain(applicationId);
              assertThat(
                      getWorkList("?assignedTo=" + caseworkerId + "&itemType=APPLICATION")
                          .getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(applicationId);
              assertThat(getWorkList("?unassigned=true&itemType=PRIOR_AUTHORITY").getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(priorAuthorityId)
                  .doesNotContain(applicationId);
              assertThat(getWorkList("?unassigned=false").getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(applicationId, priorAuthorityId);
              WorkListResponse page = getWorkList("?unassigned=false&page=1&pageSize=1");
              assertThat(page.getItems()).hasSize(1);
              assertThat(page.getPaging().getPage()).isEqualTo(1);
              assertThat(page.getPaging().getPageSize()).isEqualTo(1);
            });

    assertThat(
            restTemplate
                .exchange(
                    "http://localhost:" + port + "/api/v0/work-list?page=-1",
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            restTemplate
                .exchange(
                    "http://localhost:"
                        + port
                        + "/api/v0/work-list?assignedTo="
                        + caseworkerId
                        + "&unassigned=true",
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void givenAssignmentHistory_whenWorkIsAssigned_thenItIsAcceptedAtTheHttpBoundary() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = createCaseworker("history-work@example.com");
    createManualApplication(applicationId);
    WorkListAssignRequest request = new WorkListAssignRequest(caseworkerId, 0L);
    request.setEventHistory(
        EventHistoryRequest.builder().eventDescription("Taken for assessment").build());

    ResponseEntity<Void> response =
        restTemplate.exchange(
            assignmentUrl(applicationId, "assign"),
            HttpMethod.POST,
            new HttpEntity<>(request, headers()),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListContains("?assignedTo=" + caseworkerId, applicationId, caseworkerId, 1L);
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

  private UUID createAndSubmitPriorAuthorityDraft(UUID applicationId) {
    return createAndSubmitPriorAuthorityDraft(
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
            .justification("Interpreter costs for proceedings")
            .disbursementDetails(
                DisbursementDetails.builder()
                    .disbursementPurpose("Court interpreter")
                    .disbursementAmount(150.0)
                    .build())
            .build());
  }

  private UUID createAndSubmitPriorAuthorityDraft(CreatePriorAuthorityDraftRequest draftRequest) {
    ResponseEntity<String> draftResponse =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/prior-authorities",
            new HttpEntity<>(draftRequest, headers()),
            String.class);
    assertThat(draftResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    UUID priorAuthorityId =
        objectMapper
            .readValue(draftResponse.getBody(), SavePriorAuthorityDraftResponse.class)
            .getPriorAuthorityId();

    ResponseEntity<String> submitResponse =
        restTemplate.postForEntity(
            "http://localhost:"
                + port
                + "/api/v0/prior-authorities/"
                + priorAuthorityId
                + "/submit",
            new HttpEntity<>(draftRequest, headers()),
            String.class);

    assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    return priorAuthorityId;
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
