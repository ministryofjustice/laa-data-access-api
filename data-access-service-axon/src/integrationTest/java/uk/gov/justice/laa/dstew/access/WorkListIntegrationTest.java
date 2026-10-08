package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.applicationWithMatterPairs;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
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
import uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest;
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
    UUID caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
    createManualApplication(applicationId);
    awaitWorkListContains("", applicationId, null, 0L);

    ResponseEntity<Void> assigned =
        restTemplate.exchange(
            assignmentUrl(applicationId, "assign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(0L), headers()),
            Void.class);
    assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListContains("?assignedToMe=true&unassigned=false", applicationId, caseworkerId, 1L);
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
            new HttpEntity<>(new WorkListAssignRequest(0L), headers()),
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
    UUID caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
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
    awaitWorkListContains("?assignedToMe=true&unassigned=false", applicationId, caseworkerId, 1L);

    MakeDecisionRequest decision =
        MakeDecisionRequest.builder()
            .applicationVersion(1L)
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
              assertThat(getWorkList("?assignedToMe=true&unassigned=false").getItems())
                  .extracting(item -> item.getItemId())
                  .doesNotContain(applicationId);
            });
  }

  @Test
  void givenPriorAuthorityAssignedToCaseworker_whenDecided_thenItIsRemovedFromTheWorkQueue() {
    UUID parentApplicationId = UUID.randomUUID();
    UUID priorAuthorityId;
    UUID caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
    createGrantedApplication(parentApplicationId);
    priorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);
    awaitWorkListContains("", priorAuthorityId, null, 0L, WorkListItemType.PRIOR_AUTHORITY);

    assertThat(assign(priorAuthorityId, caseworkerId).getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListContains(
        "?assignedToMe=true&unassigned=false",
        priorAuthorityId,
        caseworkerId,
        1L,
        WorkListItemType.PRIOR_AUTHORITY);

    OffsetDateTime decidedAt = OffsetDateTime.now();
    MakePriorAuthorityDecisionRequest decision =
        new MakePriorAuthorityDecisionRequest()
            .decision(DecisionStatus.REFUSED)
            .decisionJustification("Does not meet policy guidelines")
            .amountGranted(BigDecimal.ZERO)
            .dateGranted(decidedAt)
            .eventHistory(new EventHistoryRequest())
            .priorAuthorityVersion(0L);
    ResponseEntity<Void> decided =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/prior-authorities/"
                + priorAuthorityId
                + "/decision",
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
                  .doesNotContain(priorAuthorityId);
              assertThat(getWorkList("?assignedToMe=true&unassigned=false").getItems())
                  .extracting(item -> item.getItemId())
                  .doesNotContain(priorAuthorityId);
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
                        assertThat(item.getCategoryOfLaw()).isNull();
                        assertThat(item.getCategoryOfLawCode()).isNull();
                        assertThat(item.getMatterTypes())
                            .extracting(matterType -> matterType)
                            .containsExactly("special children act (SCA)");
                        assertThat(item.getMatterTypeCodes()).containsExactly("KPBLW");
                        assertThat(item.getPriorAuthorityType())
                            .isEqualTo(PriorAuthorityType.DISBURSEMENT);
                        assertThat(item.getExpertType()).isNull();
                      });
            });
  }

  @Test
  void givenUnknownLegalCategories_whenListed_thenReturnsRawNamesAndCodes() {
    ApplicationCreateRequest request =
        applicationWithMatterPairs(
            "Category outside the old enum",
            "CATEGORY-999",
            "Matter outside the old enum",
            "MATTER-999",
            "Second matter",
            "MATTER-998");
    createManualApplication(request);
    awaitWorkListContains("", request.getId(), null, 0L);

    WorkListItem item = findWorkItem(request.getId());

    assertThat(item.getCategoryOfLaw()).isEqualTo("Category outside the old enum");
    assertThat(item.getCategoryOfLawCode()).isEqualTo("CATEGORY-999");
    assertThat(item.getMatterTypes())
        .containsExactly("Matter outside the old enum", "Second matter");
    assertThat(item.getMatterTypeCodes()).containsExactly("MATTER-999", "MATTER-998");
    assertThat(item.getReadyAt().toInstant()).isEqualTo(workListReadyAt(request.getId()));
  }

  @Test
  void givenRepeatedProceedingMatterPairs_whenListed_thenKeepsArraysAligned() {
    ApplicationCreateRequest request =
        applicationWithMatterPairs(
            "Category", "CAT-1", "Same display", "PAIR-1", "Same display", "PAIR-1");
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    List<Map<String, Object>> proceedings = new ArrayList<>();
    for (Object proceeding : (List<?>) content.get("proceedings")) {
      proceedings.add(new HashMap<>((Map<String, Object>) proceeding));
    }
    Map<String, Object> third = new HashMap<>(proceedings.getFirst());
    third.put("id", UUID.randomUUID().toString());
    third.put("leadProceeding", false);
    third.put("matterTypeCode", "PAIR-2");
    proceedings.add(third);
    content.put("proceedings", proceedings);
    request.setApplicationContent(content);
    createManualApplication(request);
    awaitWorkListContains("", request.getId(), null, 0L);

    WorkListItem item = findWorkItem(request.getId());

    assertThat(item.getMatterTypes()).containsExactly("Same display", "Same display");
    assertThat(item.getMatterTypeCodes()).containsExactly("PAIR-1", "PAIR-2");
  }

  @Test
  void givenPriorAuthority_whenListed_thenUsesParentPairsButOmitsCategory() {
    ApplicationCreateRequest request =
        applicationWithMatterPairs(
            "Parent category", "CAT-PARENT", "Parent matter", "PARENT-1", "Second", "PARENT-2");
    createGrantedApplication(request);
    UUID priorAuthorityId = createAndSubmitPriorAuthorityDraft(request.getId());
    awaitWorkListContains("", priorAuthorityId, null, 0L, WorkListItemType.PRIOR_AUTHORITY);

    WorkListItem item = findWorkItem(priorAuthorityId);

    assertThat(item.getCategoryOfLaw()).isNull();
    assertThat(item.getCategoryOfLawCode()).isNull();
    assertThat(item.getMatterTypes()).containsExactly("Parent matter", "Second");
    assertThat(item.getMatterTypeCodes()).containsExactly("PARENT-1", "PARENT-2");
    assertThat(item.getReadyAt().toInstant()).isEqualTo(workListReadyAt(priorAuthorityId));
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
                                .totalAmount(BigDecimal.valueOf(100.0))
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
    UUID caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
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
    UUID claimantId = TestJwtDecoderConfig.CASEWORKER_ID;
    UUID otherCaseworkerId = TestJwtDecoderConfig.OTHER_CASEWORKER_ID;
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
            new HttpEntity<>(new WorkListAssignRequest(0L), headers()),
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

              WorkListResponse claimantQueue = getWorkList("?assignedToMe=true&unassigned=false");
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
                  getWorkList("?assignedToMe=true&unassigned=false", otherCaseworkerId);
              assertThat(otherCaseworkerQueue.getItems())
                  .extracting(WorkListItem::getItemId)
                  .doesNotContain(claimedPriorAuthorityId);
            });
  }

  @Test
  void
      givenMultiplePriorAuthoritiesAssignedToOneCaseworker_whenPersonalQueueIsViewed_thenAllAreReturned() {
    UUID parentApplicationId = UUID.randomUUID();
    UUID caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
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
                assertThat(getWorkList("?assignedToMe=true&unassigned=false").getItems())
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
    UUID caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
    createManualApplication(manualApplicationId);
    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

    assertThat(assign(manualApplicationId, caseworkerId).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(assign(priorAuthorityId, caseworkerId).getStatusCode()).isEqualTo(HttpStatus.OK);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              WorkListResponse personalQueue = getWorkList("?assignedToMe=true&unassigned=false");
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
    UUID initialApplicationCaseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
    UUID priorAuthorityCaseworkerId = TestJwtDecoderConfig.OTHER_CASEWORKER_ID;
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
              assertThat(getWorkList("?assignedToMe=true&unassigned=false").getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(manualApplicationId)
                  .doesNotContain(priorAuthorityId);
              assertThat(
                      getWorkList("?assignedToMe=true&unassigned=false", priorAuthorityCaseworkerId)
                          .getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(priorAuthorityId)
                  .doesNotContain(manualApplicationId);
            });
  }

  @Test
  void
      givenGrantedApplicationWithHistoricOwnership_whenPriorAuthorityOwnershipIsEstablished_thenApplicationHistoryIsUnchanged() {
    UUID parentApplicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    UUID initialApplicationCaseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
    UUID priorAuthorityCaseworkerId = TestJwtDecoderConfig.OTHER_CASEWORKER_ID;

    createManualApplication(parentApplicationId, proceedingId);

    assertThat(assign(parentApplicationId, initialApplicationCaseworkerId).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    awaitWorkListContains(
        "?assignedToMe=true&unassigned=false",
        parentApplicationId,
        initialApplicationCaseworkerId,
        1L);

    grantApplication(parentApplicationId, proceedingId);

    List<Map<String, Object>> historyBeforePriorAuthorityAssignment =
        await()
            .atMost(15, TimeUnit.SECONDS)
            .until(
                () -> queryApplicationHistory(parentApplicationId),
                history ->
                    history.stream()
                        .anyMatch(
                            row ->
                                "APPLICATION_MAKE_DECISION_GRANTED".equals(row.get("event_type"))));

    UUID priorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

    assertThat(assign(priorAuthorityId, priorAuthorityCaseworkerId).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        getWorkList(
                                "?assignedToMe=true&unassigned=false", priorAuthorityCaseworkerId)
                            .getItems())
                    .extracting(WorkListItem::getItemId)
                    .contains(priorAuthorityId));

    List<Map<String, Object>> historyAfterPriorAuthorityAssignment =
        queryApplicationHistory(parentApplicationId);

    assertThat(historyAfterPriorAuthorityAssignment)
        .as("Initial Application's historic ownership information remains unchanged")
        .isEqualTo(historyBeforePriorAuthorityAssignment);
  }

  private void createManualApplication(UUID applicationId, UUID proceedingId) {
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
  }

  private void grantApplication(UUID applicationId, UUID proceedingId) {
    MakeDecisionRequest decision =
        MakeDecisionRequest.builder()
            .applicationVersion(1L)
            .overallDecision(DecisionStatus.GRANTED)
            .certificate(Map.of("certificateNumber", "PA-CERT-001"))
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .proceedings(
                List.of(
                    MakeDecisionProceedingRequest.builder()
                        .proceedingId(proceedingId)
                        .meritsDecision(
                            MeritsDecisionDetailsRequest.builder()
                                .decision(MeritsDecisionStatus.GRANTED)
                                .justification("Decision approved")
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
  }

  private List<Map<String, Object>> queryApplicationHistory(UUID applicationId) {
    return jdbcTemplate.queryForList(
        "SELECT event_id, event_type, caseworker_id, occurred_at "
            + "FROM axon.application_history WHERE application_id = ? "
            + "ORDER BY occurred_at, event_id",
        applicationId);
  }

  @Test
  void
      givenOpenAndAssignedWork_whenCombinedPersonalAndOpenApplicationsAreViewed_thenOnlyOpenAndCallersWorkIsReturned() {
    UUID callersApplicationId = UUID.randomUUID();
    UUID openApplicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    UUID otherCaseworkersPriorAuthorityId;
    createManualApplication(callersApplicationId);
    createManualApplication(openApplicationId);
    createGrantedApplication(parentApplicationId);
    otherCaseworkersPriorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

    assertThat(assign(callersApplicationId, TestJwtDecoderConfig.CASEWORKER_ID).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            assign(otherCaseworkersPriorAuthorityId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(getWorkList("?assignedToMe=true&unassigned=true").getItems())
                    .extracting(WorkListItem::getItemId)
                    .contains(callersApplicationId, openApplicationId)
                    .doesNotContain(otherCaseworkersPriorAuthorityId));
  }

  @Test
  void givenMixedWorkList_whenFilteredAndPaged_thenPublicQueueContractsAreApplied() {
    UUID applicationId = UUID.randomUUID();
    UUID parentApplicationId = UUID.randomUUID();
    UUID caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
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
                      getWorkList("?assignedToMe=true&unassigned=false&itemType=APPLICATION")
                          .getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(applicationId);
              assertThat(getWorkList("?unassigned=true&itemType=PRIOR_AUTHORITY").getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(priorAuthorityId)
                  .doesNotContain(applicationId);
              assertThat(getWorkList("?assignedToMe=true&unassigned=true").getItems())
                  .extracting(WorkListItem::getItemId)
                  .contains(applicationId, priorAuthorityId);
              WorkListResponse page =
                  getWorkList("?assignedToMe=true&unassigned=true&page=1&pageSize=1");
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
                        + "/api/v0/work-list?assignedToMe=false&unassigned=false",
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void givenAssignmentHistory_whenWorkIsAssigned_thenItIsAcceptedAtTheHttpBoundary() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
    createManualApplication(applicationId);
    WorkListAssignRequest request = new WorkListAssignRequest(0L);
    request.setEventHistory(
        EventHistoryRequest.builder().eventDescription("Taken for assessment").build());

    ResponseEntity<Void> response =
        restTemplate.exchange(
            assignmentUrl(applicationId, "assign"),
            HttpMethod.POST,
            new HttpEntity<>(request, headers()),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListContains("?assignedToMe=true&unassigned=false", applicationId, caseworkerId, 1L);
  }

  @Test
  void
      givenSubmittedPriorAuthorityAssignedToCaseworker_whenDecided_thenItIsRemovedAndCannotBeAssignedAgain() {
    UUID parentApplicationId = UUID.randomUUID();
    UUID caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
    createGrantedApplication(parentApplicationId);
    UUID priorAuthorityId = createAndSubmitPriorAuthorityDraft(parentApplicationId);

    assertThat(assign(priorAuthorityId, caseworkerId).getStatusCode()).isEqualTo(HttpStatus.OK);

    MakePriorAuthorityDecisionRequest decisionRequest =
        new MakePriorAuthorityDecisionRequest()
            .decision(DecisionStatus.GRANTED)
            .decisionJustification("Granted")
            .amountGranted(BigDecimal.valueOf(150.0))
            .dateGranted(OffsetDateTime.now())
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .priorAuthorityVersion(0L);

    ResponseEntity<Void> decided =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/prior-authorities/"
                + priorAuthorityId
                + "/decision",
            HttpMethod.PATCH,
            new HttpEntity<>(decisionRequest, headersFor(caseworkerId)),
            Void.class);
    assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(getWorkList("").getItems())
                  .extracting(WorkListItem::getItemId)
                  .doesNotContain(priorAuthorityId);
              assertThat(getWorkList("?assignedTo=" + caseworkerId).getItems())
                  .extracting(WorkListItem::getItemId)
                  .doesNotContain(priorAuthorityId);
            });

    ResponseEntity<Void> reassigned =
        restTemplate.exchange(
            assignmentUrl(priorAuthorityId, "assign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(1L), headersFor(caseworkerId)),
            Void.class);
    assertThat(reassigned.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private void createManualApplication(UUID applicationId) {
    createManualApplication(validCreateApplicationRequest(applicationId, UUID.randomUUID()));
  }

  private void createManualApplication(ApplicationCreateRequest request) {
    ResponseEntity<Void> created =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<Void> ready =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications/"
                + request.getId()
                + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL), headers()),
            Void.class);
    assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private void createGrantedApplication(UUID applicationId) {
    createGrantedApplication(validCreateApplicationRequest(applicationId, UUID.randomUUID()));
  }

  private void createGrantedApplication(ApplicationCreateRequest request) {
    ResponseEntity<Void> created =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    markApplicationAutoGranted(request.getId());
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
                    .disbursementAmount(BigDecimal.valueOf(150.0))
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
    awaitWorkListContains(
        query,
        applicationId,
        expectedAssignee,
        expectedAssignmentVersion,
        WorkListItemType.APPLICATION);
  }

  private void awaitWorkListContains(
      String query,
      UUID itemId,
      UUID expectedAssignee,
      long expectedAssignmentVersion,
      WorkListItemType expectedItemType) {
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
                  .filteredOn(item -> item.getItemId().equals(itemId))
                  .singleElement()
                  .satisfies(
                      item -> {
                        assertThat(item.getItemType()).isEqualTo(expectedItemType);
                        assertThat(item.getAssignedTo()).isEqualTo(expectedAssignee);
                        assertThat(item.getAssignmentVersion())
                            .isEqualTo(expectedAssignmentVersion);
                      });
            });
  }

  private WorkListResponse getWorkList(String query) {
    return getWorkList(query, TestJwtDecoderConfig.CASEWORKER_ID);
  }

  private WorkListResponse getWorkList(String query, UUID caseworkerId) {
    ResponseEntity<WorkListResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/work-list" + query,
            HttpMethod.GET,
            new HttpEntity<>(headersFor(caseworkerId)),
            WorkListResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    return response.getBody();
  }

  private WorkListItem findWorkItem(UUID itemId) {
    return getWorkList("").getItems().stream()
        .filter(item -> item.getItemId().equals(itemId))
        .findFirst()
        .orElseThrow();
  }

  private Instant workListReadyAt(UUID itemId) {
    return jdbcTemplate.queryForObject(
        "SELECT ready_at FROM axon.work_list_item WHERE item_id = ?", Instant.class, itemId);
  }

  private String assignmentUrl(UUID itemId, String operation) {
    return "http://localhost:" + port + "/api/v0/work-list/" + itemId + "/" + operation;
  }

  private ResponseEntity<Void> assign(UUID itemId, UUID caseworkerId) {
    return restTemplate.exchange(
        assignmentUrl(itemId, "assign"),
        HttpMethod.POST,
        new HttpEntity<>(new WorkListAssignRequest(0L), headersFor(caseworkerId)),
        Void.class);
  }

  private HttpHeaders headers() {
    return headersFor(TestJwtDecoderConfig.CASEWORKER_ID);
  }

  private HttpHeaders headersFor(UUID caseworkerId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(
        caseworkerId.equals(TestJwtDecoderConfig.OTHER_CASEWORKER_ID)
            ? TestJwtDecoderConfig.OTHER_BEARER_TOKEN
            : TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}
