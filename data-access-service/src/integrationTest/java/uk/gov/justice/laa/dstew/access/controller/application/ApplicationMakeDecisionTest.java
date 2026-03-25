package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoContent;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
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
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.testDto.certificate.CertificateContent;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

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

      MakeDecisionRequest makeDecisionRequest =
          DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> builder
              .eventHistory(EventHistory.builder()
                  .eventDescription("refusal event")
                  .build())
              .overallDecision(DecisionStatus.REFUSED)
              .proceedings(List.of(
                  createMakeDecisionProceeding(UUID.randomUUID(), MeritsDecisionStatus.GRANTED, "justification 1", "reason 1")
              ))
              .autoGranted(true));

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION,
                                    makeDecisionRequest,
                                    ServiceNameHeader(serviceName),
                                    UUID.randomUUID());
        applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenMakeDecisionRequestWithMissingJustification_whenAssignDecision_thenReturnBadRequest() throws Exception {

        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.isAutoGranted(false);
            builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS);
            builder.caseworker(CaseworkerJohnDoe);
        });

        ProceedingEntity grantedProceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

      MakeDecisionRequest makeDecisionRequest =
          DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> builder
              .eventHistory(EventHistory.builder()
                  .eventDescription("refusal event")
                  .build())
              .overallDecision(DecisionStatus.REFUSED)
              .proceedings(List.of(
                  createMakeDecisionProceeding(grantedProceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "", "refusal 1")
              ))
              .autoGranted(true));

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

        // then
        assertSecurityHeaders(result);
        assertEquals(400, result.getResponse().getStatus());
      assertEquals(0L, applicationEntity.getVersion());
        ValidationException validationException = (ValidationException) result.getResolvedException();
        Assertions.assertThat(validationException.getMessage()).contains("One or more validation rules were violated");

        Assertions.assertThat(validationException.errors())
                .isInstanceOf(List.class)
                .contains("The Make Decision request must contain a refusal justification for proceeding with id: " + grantedProceedingEntity.getId());
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
            builder.caseworker(CaseworkerJohnDoe);
        });

        ProceedingEntity grantedProceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

      MakeDecisionRequest makeDecisionRequest =
          DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> builder
              .eventHistory(EventHistory.builder()
                  .eventDescription("refusal event")
                  .build())
              .overallDecision(DecisionStatus.REFUSED)
              .proceedings(List.of(
                  createMakeDecisionProceeding(grantedProceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "justification 1",
                      "reason 1")
              ))
              .autoGranted(true));

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
            builder.caseworker(CaseworkerJohnDoe);
        });

        ProceedingEntity refusedProceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        ProceedingEntity grantedProceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

      MakeDecisionRequest makeDecisionRequest =
          DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> builder
              .eventHistory(EventHistory.builder()
                  .eventDescription("refusal event")
                  .build())
              .overallDecision(DecisionStatus.REFUSED)
              .proceedings(List.of(
                  createMakeDecisionProceeding(grantedProceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "justification 1",
                      "reason 1"),
                  createMakeDecisionProceeding(refusedProceedingEntity.getId(), MeritsDecisionStatus.REFUSED, "justification 2",
                      "reason 2")
              ))
              .autoGranted(true));

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
        ApplicationEntity initialApplicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
            builder.caseworker(CaseworkerJohnDoe);
        });

        ProceedingEntity proceedingEntityOne = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(initialApplicationEntity.getId()));

        MeritsDecisionEntity meritsDecisionEntityOne = persistedDataGenerator.createAndPersist(
            MeritsDecisionsEntityGenerator.class,
            builder -> { builder
                        .proceeding(proceedingEntityOne)
                        .decision(MeritsDecisionStatus.REFUSED);
                }
        );

        ProceedingEntity proceedingEntityTwo = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(initialApplicationEntity.getId()));

        DecisionEntity decision = persistedDataGenerator.createAndPersist(
            DecisionEntityGenerator.class,
                builder -> { builder
                        .meritsDecisions(Set.of(meritsDecisionEntityOne))
                        .overallDecision(DecisionStatus.REFUSED);
                }
        );

        initialApplicationEntity.setDecision(decision);
        ApplicationEntity applicationEntity = applicationRepository.saveAndFlush(initialApplicationEntity);

        Long currentVersion = applicationEntity.getVersion();
        MakeDecisionRequest assignDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .overallDecision(DecisionStatus.REFUSED)
                    .eventHistory(EventHistory.builder()
                            .eventDescription("refusal event")
                            .build())
                    .proceedings(List.of(
                            createMakeDecisionProceeding(proceedingEntityTwo.getId(), MeritsDecisionStatus.REFUSED, "justification new", "reason new"),
                            createMakeDecisionProceeding(proceedingEntityOne.getId(), MeritsDecisionStatus.GRANTED, "justification update", "reason update")
                    ))
                    .applicationVersion(currentVersion)
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
            )))
            .caseworker(CaseworkerJohnDoe);
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
      assertEquals(0L, applicationEntity.getVersion());
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
    public void givenApplicationWithNoCaseworker_whenAssignDecisionApplication_thenReturnNotFoundAndMessage()
        throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                "test", "content"
            )));
        });

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
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
      assertEquals(0L, applicationEntity.getVersion());
        assertEquals("application/problem+json", result.getResponse().getContentType());
        ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
        assertEquals("Caseworker not found for application id: " + applicationEntity.getId(), problemDetail.getDetail());
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
                .ignoringFields("certificate", "applicationVersion")
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

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenGrantedDecisionWithNoCertificate_whenAssignDecision_thenReturnBadRequest()
            throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
            builder.caseworker(CaseworkerJohnDoe);
        });

        ProceedingEntity proceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .eventHistory(EventHistory.builder()
                            .eventDescription("granted event")
                            .build())
                    .overallDecision(DecisionStatus.GRANTED)
                    .proceedings(List.of(
                            createMakeDecisionProceeding(proceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "justification 1", "reason 1")
                    ))
                    .certificate(null)
                    .autoGranted(false);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
        assertEquals("application/problem+json", result.getResponse().getContentType());

        ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) problemDetail.getProperties().get("errors");
        Assertions.assertThat(errors).contains("The Make Decision request must contain a certificate when overallDecision is GRANTED");

        // Verify no certificate was persisted
        List<CertificateEntity> certificates = certificateRepository.findAll();
        assertThat(certificates.size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenGrantedDecisionWithCertificate_whenAssignDecision_thenReturnNoContent_andDecisionAndCertificateSaved()
            throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
            builder.caseworker(CaseworkerJohnDoe);
        });

        ProceedingEntity proceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        CertificateContent expectedCertificateContent = DataGenerator.createDefault(CertificateContentGenerator.class);

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .eventHistory(EventHistory.builder()
                            .eventDescription("granted event")
                            .build())
                    .overallDecision(DecisionStatus.GRANTED)
                    .proceedings(List.of(
                            createMakeDecisionProceeding(proceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "justification 1", "reason 1")
                    ))
                    .certificate(objectMapper.convertValue(expectedCertificateContent, Map.class))
                    .autoGranted(false);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertNoContent(result);

        ApplicationEntity updatedApplicationEntity = applicationRepository.findById(applicationEntity.getId()).orElseThrow();
        assertEquals(ApplicationStatus.APPLICATION_SUBMITTED, updatedApplicationEntity.getStatus());
        assertEquals(false, updatedApplicationEntity.getIsAutoGranted());
        assertThat(decisionRepository.countById(updatedApplicationEntity.getDecision().getId())).isEqualTo(1);

        verifyDecisionSavedCorrectly(
                applicationEntity.getId(),
                makeDecisionRequest
        );

        verifyCertificateSavedCorrectly(applicationEntity.getId());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenGrantedDecisionCalledTwice_whenAssignDecision_thenCertificateIsUpdatedNotDuplicated()
            throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
            builder.caseworker(CaseworkerJohnDoe);
        });

        ProceedingEntity proceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        CertificateContent originalCertificateContent = DataGenerator.createDefault(CertificateContentGenerator.class);

        MakeDecisionRequest firstMakeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .eventHistory(EventHistory.builder()
                            .eventDescription("granted event")
                            .build())
                    .overallDecision(DecisionStatus.GRANTED)
                    .proceedings(List.of(
                            createMakeDecisionProceeding(proceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "justification 1", "reason 1")
                    ))
                    .certificate(objectMapper.convertValue(originalCertificateContent, Map.class))
                    .autoGranted(false);
        });

        // First call - creates the certificate
        MvcResult firstResult = patchUri(TestConstants.URIs.ASSIGN_DECISION, firstMakeDecisionRequest, applicationEntity.getId());
        assertNoContent(firstResult);

        List<CertificateEntity> certificatesAfterFirst = certificateRepository.findAll();
        assertThat(certificatesAfterFirst.size()).isEqualTo(1);
        UUID originalCertificateId = certificatesAfterFirst.get(0).getId();

        // Updated certificate content using generator with customised values
        CertificateContent updatedCertificateContent = DataGenerator.createDefault(CertificateContentGenerator.class, builder ->
                builder.certificateNumber("UPDATEDCERT002")
                        .issueDate("2026-06-01")
                        .validUntil("2027-06-01")
        );
        Map<String, Object> updatedCertificateData = objectMapper.convertValue(updatedCertificateContent, Map.class);

        ApplicationEntity refreshedApplication = applicationRepository.findById(applicationEntity.getId()).orElseThrow();
        Long currentVersion = refreshedApplication.getVersion();

        MakeDecisionRequest secondMakeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .eventHistory(EventHistory.builder()
                            .eventDescription("granted event updated")
                            .build())
                    .overallDecision(DecisionStatus.GRANTED)
                    .proceedings(List.of(
                            createMakeDecisionProceeding(proceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "justification 2", "reason 2")
                    ))
                    .certificate(updatedCertificateData)
                    .applicationVersion(currentVersion)
                    .autoGranted(false);
        });

        // Second call - should update the existing certificate
        MvcResult secondResult = patchUri(TestConstants.URIs.ASSIGN_DECISION, secondMakeDecisionRequest, applicationEntity.getId());

        // then
        assertNoContent(secondResult);

        // Verify only one certificate exists (updated, not duplicated)
        List<CertificateEntity> certificatesAfterSecond = certificateRepository.findAll();
        assertThat(certificatesAfterSecond.size()).isEqualTo(1);

        CertificateEntity updatedCertificate = certificatesAfterSecond.get(0);
        assertThat(updatedCertificate.getId()).isEqualTo(originalCertificateId);
        assertThat(updatedCertificate.getApplicationId()).isEqualTo(applicationEntity.getId());
        assertThat(updatedCertificate.getCertificateContent().get("certificateNumber"))
                .isEqualTo(updatedCertificateContent.getCertificateNumber());
        assertThat(updatedCertificate.getCertificateContent().get("issueDate"))
                .isEqualTo(updatedCertificateContent.getIssueDate());
        assertThat(updatedCertificate.getCertificateContent().get("validUntil"))
                .isEqualTo(updatedCertificateContent.getValidUntil());
        assertThat(updatedCertificate.getUpdatedBy()).isEqualTo(CaseworkerJohnDoe.getId().toString());
    }

    private void verifyCertificateSavedCorrectly(UUID applicationId) {
        List<CertificateEntity> certificates = certificateRepository.findAll();
        assertThat(certificates.size()).isEqualTo(1);

        CertificateEntity certificate = certificates.get(0);
        assertThat(certificate.getApplicationId()).isEqualTo(applicationId);
        assertThat(certificate.getCertificateContent()).isNotNull();
        assertThat(certificate.getCertificateContent().get("certificateNumber")).isEqualTo("TESTCERT001");
        assertThat(certificate.getCertificateContent().get("issueDate")).isEqualTo("2026-03-03");
        assertThat(certificate.getCertificateContent().get("validUntil")).isEqualTo("2027-03-03");
        assertThat(certificate.getCreatedBy()).isEqualTo(CaseworkerJohnDoe.getId().toString());
        assertThat(certificate.getUpdatedBy()).isEqualTo(CaseworkerJohnDoe.getId().toString());
        assertThat(certificate.getCreatedAt()).isNotNull();
        assertThat(certificate.getModifiedAt()).isNotNull();
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenRefusedDecisionWithNoCertificate_whenAssignDecision_thenReturnNoContent()
            throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
            builder.caseworker(CaseworkerJohnDoe);
        });

        ProceedingEntity proceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .eventHistory(EventHistory.builder()
                            .eventDescription("refusal event")
                            .build())
                    .overallDecision(DecisionStatus.REFUSED)
                    .proceedings(List.of(
                            createMakeDecisionProceeding(proceedingEntity.getId(), MeritsDecisionStatus.REFUSED, "justification 1", "reason 1")
                    ))
                    .certificate(null)
                    .autoGranted(false);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertNoContent(result);

        // Verify no certificate was persisted
        List<CertificateEntity> certificates = certificateRepository.findAll();
        assertThat(certificates.size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenPartiallyGrantedDecisionWithNoCertificate_whenAssignDecision_thenReturnNoContent()
            throws Exception {
        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.applicationContent(new HashMap<>(Map.of(
                    "test", "content"
            )));
            builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
            builder.caseworker(CaseworkerJohnDoe);
        });

        ProceedingEntity proceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));

        MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder -> {
            builder
                    .eventHistory(EventHistory.builder()
                            .eventDescription("partially granted event")
                            .build())
                    .overallDecision(DecisionStatus.PARTIALLY_GRANTED)
                    .proceedings(List.of(
                            createMakeDecisionProceeding(proceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "justification 1", "reason 1")
                    ))
                    .certificate(null)
                    .autoGranted(false);
        });

        // when
        MvcResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertNoContent(result);

        // Verify no certificate was persisted (only GRANTED requires certificate)
        List<CertificateEntity> certificates = certificateRepository.findAll();
        assertThat(certificates.size()).isEqualTo(0);
    }
}
