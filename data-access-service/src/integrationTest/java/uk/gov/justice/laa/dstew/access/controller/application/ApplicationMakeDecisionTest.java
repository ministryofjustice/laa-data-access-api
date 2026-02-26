package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.*;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationMakeDecisionRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.*;

@ActiveProfiles("test")
public class ApplicationMakeDecisionTest extends BaseIntegrationTest {

    @ParameterizedTest
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    void givenMakeDecisionRequestAndInvalidHeader_whenAssignDecision_thenReturnBadRequest(
            String serviceName
    ) throws Exception {
        verifyBadServiceNameHeader(serviceName);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    void givenMakeDecisionRequestAndNoHeader_whenAssignDecision_thenReturnBadRequest() throws Exception {
        verifyBadServiceNameHeader(null);
    }

    private void verifyBadServiceNameHeader(String serviceName) throws Exception {

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .userId(UUID.randomUUID())
                    .eventHistory(EventHistory.builder()
                            .eventDescription("refusal event")
                            .build())
                    .overallDecision(DecisionStatus.REFUSED)
                    .proceedings(List.of(
                            createMakeDecisionProceeding(UUID.randomUUID(), MeritsDecisionStatus.GRANTED, "justification 1", "reason 1")
                    ))
                    .autoGranted(true);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION,
                                    makeDecisionRequest,
                                    ServiceNameHeader(serviceName),
                                    UUID.randomUUID());
        applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
    }

    private static Stream<Arguments> missingRefusalDetails() {
        return Stream.of(
                Arguments.of("", "",
                        "The Make Decision request must contain a refusal reason for proceeding with id: "),
                Arguments.of("", "justification 1",
                        "The Make Decision request must contain a refusal reason for proceeding with id: "),
                Arguments.of("refusal 1", "",
                        "The Make Decision request must contain a refusal justification for proceeding with id: ")
        );
    }

    @ParameterizedTest
    @MethodSource("missingRefusalDetails")
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenMakeDecisionRequest_whenAssignDecision_thenReturnBadRequest(
            String reason,
            String justification,
            String errorMessage
    ) throws Exception {

        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.isAutoGranted(false);
            builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS);
        });

        ProceedingEntity grantedProceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .userId(CaseworkerJohnDoe.getId())
                    .eventHistory(EventHistory.builder()
                            .eventDescription("refusal event")
                            .build())
                    .overallDecision(DecisionStatus.REFUSED)
                    .proceedings(List.of(
                            createMakeDecisionProceeding(grantedProceedingEntity.getId(), MeritsDecisionStatus.GRANTED, justification, reason)
                    ))
                    .autoGranted(true);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

        // then
        assertSecurityHeaders(result);
        assertEquals(400, result.getResponse().getStatus());
        ValidationException validationException = (ValidationException) result.getResolvedException();
        Assertions.assertThat(validationException.getMessage()).contains("One or more validation rules were violated");

        Assertions.assertThat(validationException.errors())
                .isInstanceOf(List.class)
                .contains(errorMessage + grantedProceedingEntity.getId());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenMakeDecisionRequest_whenAssignDecision_thenUpdateApplicationEntity() throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.isAutoGranted(false);
            builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
        });

        ProceedingEntity grantedProceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .userId(CaseworkerJohnDoe.getId())
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
        assertEquals(ApplicationStatus.APPLICATION_SUBMITTED, actualApplication.getStatus());
        assertEquals(true, actualApplication.getIsAutoGranted());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenMakeDecisionRequestWithTwoProceedings_whenAssignDecision_thenReturnNoContent_andDecisionSaved()
            throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
        });

        ProceedingEntity refusedProceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        ProceedingEntity grantedProceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .userId(CaseworkerJohnDoe.getId())
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
        assertEquals(ApplicationStatus.APPLICATION_IN_PROGRESS, actualApplication.getStatus());
        ApplicationEntity updatedApplicationEntity = applicationRepository.findById(applicationEntity.getId()).orElseThrow();
        assertThat(decisionRepository.countById(updatedApplicationEntity.getDecision().getId())).isEqualTo(1);

        domainEventAsserts.assertDomainEventsCreatedForApplications(
                List.of(applicationEntity),
                BaseIntegrationTest.CaseworkerJohnDoe.getId(),
                DomainEventType.APPLICATION_MAKE_DECISION_REFUSED,
                makeDecisionRequest.getEventHistory()
        );

        verifyDecisionSavedCorrectly(
                applicationEntity.getId(),
                makeDecisionRequest
        );
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenMakeDecisionRequestWithExistingContentAndNewContent_whenAssignDecision_thenReturnNoContent_andDecisionUpdated()
            throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
        });

        ProceedingEntity proceedingEntityOne = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        MeritsDecisionEntity meritsDecisionEntityOne = persistedMeritsDecisionFactory.createAndPersist(
                builder -> { builder
                        .proceeding(proceedingEntityOne)
                        .decision(MeritsDecisionStatus.REFUSED);
                }
        );

        ProceedingEntity proceedingEntityTwo = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        DecisionEntity decision = persistedDecisionFactory.createAndPersist(
                builder -> { builder
                        .meritsDecisions(Set.of(meritsDecisionEntityOne))
                        .overallDecision(DecisionStatus.REFUSED);
                }
        );

        applicationEntity.setDecision(decision);
        applicationRepository.save(applicationEntity);

        MakeDecisionRequest assignDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .userId(CaseworkerJohnDoe.getId())
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

        assertThat(decisionRepository.countById(applicationEntity.getDecision().getId()))
                .isEqualTo(1);

        domainEventAsserts.assertDomainEventsCreatedForApplications(
                List.of(applicationEntity),
                BaseIntegrationTest.CaseworkerJohnDoe.getId(),
                DomainEventType.APPLICATION_MAKE_DECISION_REFUSED,
                assignDecisionRequest.getEventHistory()
        );

        verifyDecisionSavedCorrectly(
                applicationEntity.getId(),
                assignDecisionRequest
        );
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenProceedingsNotFoundAndNotLinkedToApplication_whenMakeDecision_thenReturnNotFoundWithAllIds()
        throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                "test", "content"
            )));
        });

        ApplicationEntity unrelatedApplicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                "test", "other"
            )));
        });

        ProceedingEntity proceedingNotLinkedToApplication = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
            builder -> builder.applicationId(unrelatedApplicationEntity.getId()));

        UUID proceedingIdNotFound = UUID.randomUUID();

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                .userId(CaseworkerJohnDoe.getId())
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
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenNoApplication_whenAssignDecisionApplication_thenReturnNotFoundAndMessage()
            throws Exception {
        // given
        UUID applicationId = UUID.randomUUID();
        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .userId(CaseworkerJohnDoe.getId())
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
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenNoCaseworker_whenAssignDecisionApplication_thenReturnNotFoundAndMessage()
            throws Exception {
        // given
        UUID caseworkerId = UUID.randomUUID();

        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
        });

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .userId(caseworkerId)
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
                            .justification(justification)
                            .reason(reason)
                            .build()
                )
                .build();
    }

    private void verifyDecisionSavedCorrectly(UUID applicationId, MakeDecisionRequest expectedMakeDecisionRequest) {
        ApplicationEntity updatedApplicationEntity = applicationRepository.findById(applicationId).orElseThrow();

        DecisionEntity savedDecision = updatedApplicationEntity.getDecision();

        MakeDecisionRequest actual = mapToMakeDecisionRequest(
                savedDecision,
                updatedApplicationEntity,
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
                .overallDecision(decisionEntity.getOverallDecision())
                .userId(CaseworkerJohnDoe.getId())
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
                .reason(entity.getReason())
                .justification(entity.getJustification())
                .build();
    }
}
