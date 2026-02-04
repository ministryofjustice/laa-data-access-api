package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.http.ProblemDetail;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetails;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.RefusalDetails;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoContent;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;

@ActiveProfiles("test")
public class ApplicationMakeDecisionTest extends BaseIntegrationTest {

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenMakeDecisionRequest_whenAssignDecision_thenUpdateApplicationEntity() throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedApplicationFactory.createAndPersist(builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.isAutoGranted(false);
            builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
        });

        ProceedingEntity grantedProceedingEntity = persistedProceedingFactory.createAndPersist(
                builder -> { builder
                        .applicationId(applicationEntity.getId()); }
        );

        MakeDecisionRequest makeDecisionRequest = makeDecisionRequestFactory.create(builder -> {
            builder
                    .userId(CaseworkerJohnDoe.getId())
                    .applicationStatus(ApplicationStatus.APPLICATION_IN_PROGRESS)
                    .eventHistory(EventHistory.builder()
                            .eventDescription("refusal event")
                            .build())
                    .overallDecision(DecisionStatus.REFUSED)
                    .proceedings(List.of(
                            createMakeDecisionProceeding(grantedProceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "justification 1", "reason 1")
                    ))
                    .autoGranted(true);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

        //then
        ApplicationEntity actualApplication = applicationRepository.findById(applicationEntity.getId()).orElseThrow();
        assertEquals(ApplicationStatus.APPLICATION_IN_PROGRESS, actualApplication.getStatus());
        assertEquals(true, actualApplication.getIsAutoGranted());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenMakeDecisionRequestWithTwoProceedings_whenAssignDecision_thenReturnNoContent_andDecisionSaved()
            throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedApplicationFactory.createAndPersist(builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
        });

        ProceedingEntity refusedProceedingEntity = persistedProceedingFactory.createAndPersist(
                builder -> {builder
                        .applicationId(applicationEntity.getId());}
        );

        ProceedingEntity grantedProceedingEntity = persistedProceedingFactory.createAndPersist(
                builder -> {builder
                        .applicationId(applicationEntity.getId());}
        );

        MakeDecisionRequest makeDecisionRequest = makeDecisionRequestFactory.create(builder -> {
            builder
                    .userId(CaseworkerJohnDoe.getId())
                    .applicationStatus(ApplicationStatus.APPLICATION_SUBMITTED)
                    .eventHistory(EventHistory.builder()
                            .eventDescription("refusal event")
                            .build())
                    .overallDecision(DecisionStatus.REFUSED)
                    .proceedings(List.of(
                            createMakeDecisionProceeding(grantedProceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "justification 1", "reason 1"),
                            createMakeDecisionProceeding(refusedProceedingEntity.getId(), MeritsDecisionStatus.REFUSED, "justification 2", "reason 2")
                    ))
                    .autoGranted(true);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertNoContent(result);

        ApplicationEntity actualApplication = applicationRepository.findById(applicationEntity.getId()).orElseThrow();
        assertEquals(ApplicationStatus.APPLICATION_SUBMITTED, actualApplication.getStatus());
        assertThat(decisionRepository.countByApplicationId(applicationEntity.getId()))
                .isEqualTo(1);

        domainEventAsserts.assertDomainEventsCreatedForApplications(
                List.of(applicationEntity),
                BaseIntegrationTest.CaseworkerJohnDoe.getId(),
                DomainEventType.APPLICATION_MAKE_DECISION_REFUSED,
                makeDecisionRequest.getEventHistory()
        );

        verifyDecisionSavedCorrectly(
                applicationEntity.getId(),
                makeDecisionRequest,
                applicationEntity,
                null
        );
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenMakeDecisionRequestWithExistingContentAndNewContent_whenAssignDecision_thenReturnNoContent_andDecisionUpdated()
            throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedApplicationFactory.createAndPersist(builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
        });

        ProceedingEntity proceedingEntityOne = persistedProceedingFactory.createAndPersist(
                builder -> {builder
                        .applicationId(applicationEntity.getId());}
        );

        ProceedingEntity proceedingEntityTwo = persistedProceedingFactory.createAndPersist(
                builder -> {builder
                        .applicationId(applicationEntity.getId());}
        );

        MeritsDecisionEntity meritsDecisionEntityOne = persistedMeritsDecisionFactory.createAndPersist(
                builder -> { builder
                        .proceeding(proceedingEntityOne)
                        .decision(MeritsDecisionStatus.REFUSED);
                }
        );

        DecisionEntity decision = persistedDecisionFactory.createAndPersist(
                builder -> { builder
                        .applicationId(applicationEntity.getId())
                        .meritsDecisions(Set.of(meritsDecisionEntityOne))
                        .overallDecision(DecisionStatus.REFUSED);
                }
        );

        MakeDecisionRequest assignDecisionRequest = makeDecisionRequestFactory.create(builder -> {
            builder
                    .userId(CaseworkerJohnDoe.getId())
                    .applicationStatus(ApplicationStatus.APPLICATION_SUBMITTED)
                    .overallDecision(DecisionStatus.REFUSED)
                    .eventHistory(EventHistory.builder()
                            .eventDescription("refusal event")
                            .build())
                    .proceedings(List.of(
                            createMakeDecisionProceeding(proceedingEntityTwo.getId(), MeritsDecisionStatus.REFUSED, "justification new", "reason new"),
                            createMakeDecisionProceeding(proceedingEntityOne.getId(), MeritsDecisionStatus.GRANTED, "justification update", "reason update")
                    ))
                    .autoGranted(true);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, assignDecisionRequest, applicationEntity.getId());

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertNoContent(result);

        assertEquals(ApplicationStatus.APPLICATION_SUBMITTED, applicationEntity.getStatus());

        assertThat(decisionRepository.countByApplicationId(applicationEntity.getId()))
                .isEqualTo(1);

        domainEventAsserts.assertDomainEventsCreatedForApplications(
                List.of(applicationEntity),
                BaseIntegrationTest.CaseworkerJohnDoe.getId(),
                DomainEventType.APPLICATION_MAKE_DECISION_REFUSED,
                assignDecisionRequest.getEventHistory()
        );

        verifyDecisionSavedCorrectly(
                applicationEntity.getId(),
                assignDecisionRequest,
                applicationEntity,
                decision
        );
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenProceedingsNotFoundAndNotLinkedToApplication_whenMakeDecision_thenReturnNotFoundWithAllIds()
        throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedApplicationFactory.createAndPersist(builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                "test", "content"
            )));
        });

        ApplicationEntity unrelatedApplicationEntity = persistedApplicationFactory.createAndPersist(builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                "test", "other"
            )));
        });

        ProceedingEntity proceedingNotLinkedToApplication = persistedProceedingFactory.createAndPersist(
            builder -> {
                builder
                    .applicationId(unrelatedApplicationEntity.getId());
            }
        );

        UUID proceedingIdNotFound = UUID.randomUUID();

        MakeDecisionRequest makeDecisionRequest = makeDecisionRequestFactory.create(builder -> {
            builder
                .userId(CaseworkerJohnDoe.getId())
                .applicationStatus(ApplicationStatus.APPLICATION_SUBMITTED)
                .overallDecision(DecisionStatus.REFUSED)
                .eventHistory(EventHistory.builder()
                    .eventDescription("refusal event")
                    .build())
                .proceedings(List.of(
                    createMakeDecisionProceeding(proceedingIdNotFound, MeritsDecisionStatus.REFUSED, "justification1",
                        "reason1"),
                    createMakeDecisionProceeding(proceedingNotLinkedToApplication.getId(), MeritsDecisionStatus.GRANTED,
                        "justification2", "reason2")
                ))
                .autoGranted(true);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertNotFound(result);
        assertEquals("application/problem+json", result.getResponse().getContentType());

        ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
        assertThat(problemDetail.getDetail())
            .contains("No proceeding found with id:")
            .contains(proceedingIdNotFound.toString())
            .contains("Not linked to application:")
            .contains(proceedingNotLinkedToApplication.getId().toString());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenNoApplication_whenAssignDecisionApplication_thenReturnNotFoundAndMessage()
            throws Exception {
        // given
        UUID applicationId = UUID.randomUUID();
        MakeDecisionRequest makeDecisionRequest = makeDecisionRequestFactory.create(builder -> {
            builder
                    .userId(CaseworkerJohnDoe.getId())
                    .applicationStatus(ApplicationStatus.APPLICATION_SUBMITTED)
                    .overallDecision(DecisionStatus.PARTIALLY_GRANTED)
                    .eventHistory(EventHistory.builder().build())
                    .proceedings(List.of(
                            createMakeDecisionProceeding(
                                    UUID.randomUUID(),
                                    MeritsDecisionStatus.REFUSED,
                                    "justification",
                                    "reason")
                    ))
                    .autoGranted(true);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationId);

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertNotFound(result);
        assertEquals("application/problem+json", result.getResponse().getContentType());
        ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
        assertEquals("No application found with id: " + applicationId, problemDetail.getDetail());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenNoCaseworker_whenAssignDecisionApplication_thenReturnNotFoundAndMessage()
            throws Exception {
        // given
        UUID caseworkerId = UUID.randomUUID();

        ApplicationEntity applicationEntity = persistedApplicationFactory.createAndPersist(builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
        });

        MakeDecisionRequest makeDecisionRequest = makeDecisionRequestFactory.create(builder -> {
            builder
                    .userId(caseworkerId)
                    .applicationStatus(ApplicationStatus.APPLICATION_SUBMITTED)
                    .overallDecision(DecisionStatus.PARTIALLY_GRANTED)
                    .eventHistory(EventHistory.builder().build())
                    .proceedings(List.of(
                            createMakeDecisionProceeding(
                                    UUID.randomUUID(),
                                    MeritsDecisionStatus.REFUSED,
                                    "justification",
                                    "reason")
                    ))
                    .autoGranted(true);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertNotFound(result);
        assertEquals("application/problem+json", result.getResponse().getContentType());
        ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
        assertEquals("No caseworker found with id: " + caseworkerId, problemDetail.getDetail());
    }

    private MakeDecisionProceeding createMakeDecisionProceeding(UUID proceedingId, MeritsDecisionStatus meritsDecisionStatus, String justification, String reason) {
        return MakeDecisionProceeding.builder()
                .proceedingId(proceedingId)
                .meritsDecision(
                        MeritsDecisionDetails.builder()
                                .decision(meritsDecisionStatus)
                                .refusal(
                                        RefusalDetails.builder()
                                                .justification(justification)
                                                .reason(reason)
                                                .build()
                                )
                                .build()
                )
                .build();
    }

    private void verifyDecisionSavedCorrectly(UUID applicationId, MakeDecisionRequest expectedMakeDecisionRequest, ApplicationEntity expectedApplicationEntity, DecisionEntity currentSavedDecisionEntity) {
        DecisionEntity savedDecision = decisionRepository.findByApplicationId(applicationId).orElseThrow();

        MakeDecisionRequest actual = mapToMakeDecisionRequest(
                savedDecision,
                expectedApplicationEntity,
                expectedMakeDecisionRequest.getEventHistory()
                );

        Assertions.assertThat(actual)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .isEqualTo(expectedMakeDecisionRequest);

        Assertions.assertThat(savedDecision.getModifiedAt()).isNotNull();
        Assertions.assertThat(savedDecision.getMeritsDecisions())
                .allSatisfy(merits -> {
                    Assertions.assertThat(merits.getModifiedAt()).isNotNull();
                });
    }

    // DecisionEntity -> MakeDecisionRequest
    private static MakeDecisionRequest mapToMakeDecisionRequest(
                                DecisionEntity decisionEntity,
                                ApplicationEntity applicationEntity,
                                EventHistory eventHistory) {
        if (decisionEntity == null) return null;
        return MakeDecisionRequest.builder()
                .applicationStatus(applicationEntity.getStatus())
                .overallDecision(decisionEntity.getOverallDecision())
                .userId(applicationEntity.getCaseworker().getId())
                .eventHistory(eventHistory)
                .proceedings(decisionEntity.getMeritsDecisions().stream()
                        .map(ApplicationMakeDecisionTest::mapToProceedingDetails)
                        .toList())
                .autoGranted(applicationEntity.getIsAutoGranted())
                .build();
    }

    // MeritsDecisionEntity -> ProceedingDetails
    private static MakeDecisionProceeding mapToProceedingDetails(MeritsDecisionEntity meritsDecisionEntity) {
        if (meritsDecisionEntity == null) return null;
        return MakeDecisionProceeding.builder()
                .proceedingId(meritsDecisionEntity.getProceeding().getId())
                .meritsDecision(mapToMeritsDecisionDetails(meritsDecisionEntity))
                .build();
    }

    // MeritsDecisionEntity -> MeritsDecisionDetails
    private static MeritsDecisionDetails mapToMeritsDecisionDetails(MeritsDecisionEntity entity) {
        if (entity == null) return null;
        return MeritsDecisionDetails.builder()
                .decision(entity.getDecision())
                .refusal(mapToRefusalDetails(entity))
                .build();
    }

    // MeritsDecisionEntity -> RefusalDetails
    private static RefusalDetails mapToRefusalDetails(MeritsDecisionEntity entity) {
        if (entity == null) return null;
        return RefusalDetails.builder()
                .reason(entity.getReason())
                .justification(entity.getJustification())
                .build();
    }
}
