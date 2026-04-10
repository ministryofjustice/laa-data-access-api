package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoContent;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationMakeDecisionRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.testDto.certificate.CertificateContent;

public class ApplicationMakeDecisionTest extends BaseHarnessTest {

  static Stream<String> invalidServiceNameHeaders() {
    return Stream.of("", "invalid-header", "CIVIL-APPLY", "civil_apply", null);
  }

  @ParameterizedTest
  @MethodSource("invalidServiceNameHeaders")
  void givenMakeDecisionRequestAndInvalidOrMissingHeader_whenMakeDecision_thenReturnBadRequest(
      String serviceName) throws Exception {
    verifyBadServiceNameHeader(serviceName);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {

    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .eventHistory(
                        EventHistoryRequest.builder().eventDescription("refusal event").build())
                    .overallDecision(DecisionStatus.REFUSED)
                    .proceedings(
                        List.of(
                            createMakeDecisionProceeding(
                                UUID.randomUUID(),
                                MeritsDecisionStatus.GRANTED,
                                "justification 1",
                                "reason 1")))
                    .autoGranted(true));

    // when
    HarnessResult result =
        patchUri(
            TestConstants.URIs.MAKE_DECISION,
            makeDecisionRequest,
            ServiceNameHeader(serviceName),
            UUID.randomUUID());
    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  @Test
  public void givenMakeDecisionRequest_whenMakeDecision_thenUpdateApplicationEntity()
      throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              builder.isAutoGranted(false);
              builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
              builder.caseworker(CaseworkerJohnDoe);
            });

    ProceedingEntity grantedProceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .eventHistory(
                        EventHistoryRequest.builder().eventDescription("refusal event").build())
                    .overallDecision(DecisionStatus.REFUSED)
                    .proceedings(
                        List.of(
                            createMakeDecisionProceeding(
                                grantedProceedingEntity.getId(),
                                MeritsDecisionStatus.GRANTED,
                                "justification 1",
                                "reason 1")))
                    .autoGranted(true));

    // when
    patchUri(TestConstants.URIs.MAKE_DECISION, makeDecisionRequest, applicationEntity.getId());

    // then
    ApplicationEntity actualApplication =
        applicationRepository.findById(applicationEntity.getId()).orElseThrow();
    assertEquals(ApplicationStatus.APPLICATION_SUBMITTED, actualApplication.getStatus());
    assertEquals(true, actualApplication.getIsAutoGranted());
  }

  @Test
  public void
      givenMakeDecisionRequestAndCertificateExists_whenAssignDecision_thenReturnNoContent_andCertificateDeleted()
          throws Exception {

    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS);
              builder.caseworker(CaseworkerJohnDoe);
            });

    ProceedingEntity proceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    CertificateContent expectedCertificateContent =
        DataGenerator.createDefault(CertificateContentGenerator.class);

    MakeDecisionRequest initialMakeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder -> {
              builder
                  .eventHistory(
                      EventHistoryRequest.builder().eventDescription("make decision event").build())
                  .overallDecision(DecisionStatus.GRANTED)
                  .proceedings(
                      List.of(
                          createMakeDecisionProceeding(
                              proceedingEntity.getId(),
                              MeritsDecisionStatus.GRANTED,
                              "justification 1",
                              "reason 1")))
                  .certificate(objectMapper.convertValue(expectedCertificateContent, Map.class))
                  .autoGranted(false);
            });

    MakeDecisionRequest secondMakeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder -> {
              builder
                  .applicationVersion(1L)
                  .eventHistory(
                      EventHistoryRequest.builder().eventDescription("make decision event").build())
                  .overallDecision(DecisionStatus.REFUSED)
                  .proceedings(initialMakeDecisionRequest.getProceedings())
                  .autoGranted(false);
            });

    // when
    patchUri(
        TestConstants.URIs.MAKE_DECISION, initialMakeDecisionRequest, applicationEntity.getId());
    HarnessResult result =
        patchUri(
            TestConstants.URIs.MAKE_DECISION, secondMakeDecisionRequest, applicationEntity.getId());

    // then
    assertNoContent(result);
    assertFalse(certificateRepository.existsByApplication_Id(applicationEntity.getId()));
  }

  @Test
  public void
      givenMakeDecisionRequestWithTwoProceedings_whenMakeDecision_thenReturnNoContent_andDecisionSaved()
          throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              builder.caseworker(CaseworkerJohnDoe);
            });

    ProceedingEntity refusedProceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    ProceedingEntity grantedProceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .eventHistory(
                        EventHistoryRequest.builder().eventDescription("refusal event").build())
                    .overallDecision(DecisionStatus.REFUSED)
                    .proceedings(
                        List.of(
                            createMakeDecisionProceeding(
                                grantedProceedingEntity.getId(),
                                MeritsDecisionStatus.GRANTED,
                                "justification 1",
                                "reason 1"),
                            createMakeDecisionProceeding(
                                refusedProceedingEntity.getId(),
                                MeritsDecisionStatus.REFUSED,
                                "justification 2",
                                "reason 2")))
                    .autoGranted(true));

    // when
    HarnessResult result =
        patchUri(TestConstants.URIs.MAKE_DECISION, makeDecisionRequest, applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);

    ApplicationEntity actualApplication =
        applicationRepository.findById(applicationEntity.getId()).orElseThrow();
    assertEquals(ApplicationStatus.APPLICATION_IN_PROGRESS, actualApplication.getStatus());
    ApplicationEntity updatedApplicationEntity =
        applicationRepository.findById(applicationEntity.getId()).orElseThrow();
    assertThat(decisionRepository.countById(updatedApplicationEntity.getDecision().getId()))
        .isEqualTo(1);

    domainEventAsserts.assertDomainEventsCreatedForApplications(
        List.of(applicationEntity),
        CaseworkerJohnDoe.getId(),
        DomainEventType.APPLICATION_MAKE_DECISION_REFUSED,
        makeDecisionRequest.getEventHistory());

    verifyDecisionSavedCorrectly(applicationEntity.getId(), makeDecisionRequest);
  }

  @Test
  public void
      givenMakeDecisionRequestWithExistingContentAndNewContent_whenMakeDecision_thenReturnNoContent_andDecisionUpdated()
          throws Exception {
    // given
    ApplicationEntity initialApplicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
              builder.caseworker(CaseworkerJohnDoe);
            });

    ProceedingEntity proceedingEntityOne =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class,
            builder -> builder.application(initialApplicationEntity));

    ProceedingEntity proceedingEntityTwo =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class,
            builder -> builder.application(initialApplicationEntity));

    DecisionEntity decision =
        persistedDataGenerator.createAndPersist(
            DecisionEntityGenerator.class,
            builder ->
                builder
                    .meritsDecisions(
                        Set.of(
                            DataGenerator.createDefault(
                                MeritsDecisionsEntityGenerator.class,
                                mBuilder ->
                                    mBuilder
                                        .proceeding(proceedingEntityOne)
                                        .decision(MeritsDecisionStatus.REFUSED))))
                    .overallDecision(DecisionStatus.REFUSED));

    initialApplicationEntity.setDecision(decision);
    ApplicationEntity applicationEntity =
        applicationRepository.saveAndFlush(initialApplicationEntity);

    Long currentVersion = applicationEntity.getVersion();
    MakeDecisionRequest assignDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .overallDecision(DecisionStatus.REFUSED)
                    .eventHistory(
                        EventHistoryRequest.builder().eventDescription("refusal event").build())
                    .proceedings(
                        List.of(
                            createMakeDecisionProceeding(
                                proceedingEntityTwo.getId(),
                                MeritsDecisionStatus.REFUSED,
                                "justification new",
                                "reason new"),
                            createMakeDecisionProceeding(
                                proceedingEntityOne.getId(),
                                MeritsDecisionStatus.GRANTED,
                                "justification update",
                                "reason update")))
                    .applicationVersion(currentVersion)
                    .autoGranted(true));

    // when
    HarnessResult result =
        patchUri(
            TestConstants.URIs.MAKE_DECISION, assignDecisionRequest, applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);

    assertEquals(ApplicationStatus.APPLICATION_SUBMITTED, applicationEntity.getStatus());

    assertThat(decisionRepository.countById(applicationEntity.getDecision().getId())).isEqualTo(1);

    domainEventAsserts.assertDomainEventsCreatedForApplications(
        List.of(applicationEntity),
        CaseworkerJohnDoe.getId(),
        DomainEventType.APPLICATION_MAKE_DECISION_REFUSED,
        assignDecisionRequest.getEventHistory());

    verifyDecisionSavedCorrectly(applicationEntity.getId(), assignDecisionRequest);
  }

  @Test
  public void
      givenProceedingsNotFoundAndNotLinkedToApplication_whenMakeDecision_thenReturnNotFoundWithAllIds()
          throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder ->
                builder
                    .applicationContent(new HashMap<>(Map.of("test", "content")))
                    .caseworker(CaseworkerJohnDoe));

    ApplicationEntity unrelatedApplicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> builder.applicationContent(new HashMap<>(Map.of("test", "other"))));

    ProceedingEntity proceedingNotLinkedToApplication =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class,
            builder -> builder.application(unrelatedApplicationEntity));

    UUID proceedingIdNotFound = UUID.randomUUID();

    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .overallDecision(DecisionStatus.REFUSED)
                    .eventHistory(
                        EventHistoryRequest.builder().eventDescription("refusal event").build())
                    .proceedings(
                        List.of(
                            createMakeDecisionProceeding(
                                proceedingIdNotFound,
                                MeritsDecisionStatus.REFUSED,
                                "justification1",
                                "reason1"),
                            createMakeDecisionProceeding(
                                proceedingNotLinkedToApplication.getId(),
                                MeritsDecisionStatus.GRANTED,
                                "justification2",
                                "reason2")))
                    .autoGranted(true));

    // when
    HarnessResult result =
        patchUri(TestConstants.URIs.MAKE_DECISION, makeDecisionRequest, applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNotFound(result);
    assertThat(result.getResponse().getHeader("Content-Type"))
        .startsWith("application/problem+json");
    assertEquals(0L, applicationEntity.getVersion());
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertThat(problemDetail.getDetail())
        .contains("No proceeding found with id:")
        .contains(proceedingIdNotFound.toString())
        .contains("Not linked to application:")
        .contains(proceedingNotLinkedToApplication.getId().toString());
  }

  @Test
  public void givenNoApplication_whenMakeDecision_thenReturnNotFoundAndMessage() throws Exception {
    // given
    UUID applicationId = UUID.randomUUID();
    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .overallDecision(DecisionStatus.REFUSED)
                    .eventHistory(EventHistoryRequest.builder().build())
                    .proceedings(
                        List.of(
                            createMakeDecisionProceeding(
                                UUID.randomUUID(),
                                MeritsDecisionStatus.REFUSED,
                                "justification",
                                "reason")))
                    .autoGranted(true));

    // when
    HarnessResult result =
        patchUri(TestConstants.URIs.MAKE_DECISION, makeDecisionRequest, applicationId);

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNotFound(result);
    assertThat(result.getResponse().getHeader("Content-Type"))
        .startsWith("application/problem+json");
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertEquals("No application found with id: " + applicationId, problemDetail.getDetail());
  }

  @Test
  @Disabled("Temporarily disabled until security is implemented")
  public void
      givenApplicationWithNoCaseworker_whenAssignDecisionApplication_thenReturnNotFoundAndMessage()
          throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> builder.applicationContent(new HashMap<>(Map.of("test", "content"))));

    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .overallDecision(DecisionStatus.REFUSED)
                    .eventHistory(EventHistoryRequest.builder().build())
                    .proceedings(
                        List.of(
                            createMakeDecisionProceeding(
                                UUID.randomUUID(),
                                MeritsDecisionStatus.REFUSED,
                                "justification",
                                "reason")))
                    .autoGranted(true));

    // when
    HarnessResult result =
        patchUri(TestConstants.URIs.MAKE_DECISION, makeDecisionRequest, applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNotFound(result);
    assertEquals(0L, applicationEntity.getVersion());
    assertThat(result.getResponse().getHeader("Content-Type"))
        .startsWith("application/problem+json");
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertEquals(
        "Caseworker not found for application id: " + applicationEntity.getId(),
        problemDetail.getDetail());
  }

  // This Test will be removed once security is implemented within the service
  @Test
  public void
      givenApplicationWithNoCaseworker_whenAssignDecision_thenReturnNoContent_andDecisionSaved()
          throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.applicationContent(new HashMap<>(Map.of("test", "content")));
            });

    ProceedingEntity refusedProceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    ProceedingEntity grantedProceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .eventHistory(
                        EventHistoryRequest.builder().eventDescription("refusal event").build())
                    .overallDecision(DecisionStatus.REFUSED)
                    .proceedings(
                        List.of(
                            createMakeDecisionProceeding(
                                grantedProceedingEntity.getId(),
                                MeritsDecisionStatus.GRANTED,
                                "justification 1",
                                "reason 1"),
                            createMakeDecisionProceeding(
                                refusedProceedingEntity.getId(),
                                MeritsDecisionStatus.REFUSED,
                                "justification 2",
                                "reason 2")))
                    .autoGranted(true));

    // when
    HarnessResult result =
        patchUri(TestConstants.URIs.MAKE_DECISION, makeDecisionRequest, applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);

    ApplicationEntity actualApplication =
        applicationRepository.findById(applicationEntity.getId()).orElseThrow();
    assertEquals(ApplicationStatus.APPLICATION_IN_PROGRESS, actualApplication.getStatus());
    ApplicationEntity updatedApplicationEntity =
        applicationRepository.findById(applicationEntity.getId()).orElseThrow();
    assertThat(decisionRepository.countById(updatedApplicationEntity.getDecision().getId()))
        .isEqualTo(1);

    domainEventAsserts.assertDomainEventsCreatedForApplications(
        List.of(applicationEntity),
        null,
        DomainEventType.APPLICATION_MAKE_DECISION_REFUSED,
        makeDecisionRequest.getEventHistory());

    verifyDecisionSavedCorrectly(applicationEntity.getId(), makeDecisionRequest);
  }

  private static MakeDecisionProceedingRequest createMakeDecisionProceeding(
      UUID proceedingId,
      MeritsDecisionStatus meritsDecisionStatus,
      String justification,
      String reason) {

    return MakeDecisionProceedingRequest.builder()
        .proceedingId(proceedingId)
        .meritsDecision(
            MeritsDecisionDetailsRequest.builder()
                .decision(meritsDecisionStatus)
                .justification(justification)
                .reason(reason)
                .build())
        .build();
  }

  private void verifyDecisionSavedCorrectly(
      UUID applicationId, MakeDecisionRequest expectedMakeDecisionRequest) {
    decisionAsserts.verifyDecisionSavedCorrectly(applicationId, expectedMakeDecisionRequest);
  }

  // DecisionEntity -> MakeDecisionRequest mapping logic lives in DecisionAsserts

  static Stream<Arguments> invalidRequestCases() {
    return Stream.of(
        Arguments.of(
            (Function<ProceedingEntity, MakeDecisionRequest>)
                p ->
                    DataGenerator.createDefault(
                        ApplicationMakeDecisionRequestGenerator.class,
                        b ->
                            b.overallDecision(DecisionStatus.REFUSED)
                                .eventHistory(
                                    EventHistoryRequest.builder()
                                        .eventDescription("refusal event")
                                        .build())
                                .proceedings(
                                    List.of(
                                        createMakeDecisionProceeding(
                                            p.getId(),
                                            MeritsDecisionStatus.GRANTED,
                                            "",
                                            "refusal 1")))
                                .autoGranted(true)),
            "The Make Decision request must contain a refusal justification for proceeding with id: %s"),
        Arguments.of(
            (Function<ProceedingEntity, MakeDecisionRequest>)
                p ->
                    DataGenerator.createDefault(
                        ApplicationMakeDecisionRequestGenerator.class,
                        b ->
                            b.overallDecision(DecisionStatus.GRANTED)
                                .eventHistory(
                                    EventHistoryRequest.builder()
                                        .eventDescription("granted event")
                                        .build())
                                .proceedings(
                                    List.of(
                                        createMakeDecisionProceeding(
                                            p.getId(),
                                            MeritsDecisionStatus.GRANTED,
                                            "justification 1",
                                            "reason 1")))
                                .certificate(null)
                                .autoGranted(false)),
            "The Make Decision request must contain a certificate when overallDecision is GRANTED"),
        Arguments.of(
            (Function<ProceedingEntity, MakeDecisionRequest>)
                p ->
                    DataGenerator.createDefault(
                        ApplicationMakeDecisionRequestGenerator.class,
                        b ->
                            b.overallDecision(DecisionStatus.GRANTED)
                                .eventHistory(
                                    EventHistoryRequest.builder()
                                        .eventDescription("granted event")
                                        .build())
                                .proceedings(
                                    List.of(
                                        createMakeDecisionProceeding(
                                            p.getId(),
                                            MeritsDecisionStatus.GRANTED,
                                            "justification 1",
                                            "reason 1")))
                                .certificate(Map.of())
                                .autoGranted(false)),
            "The Make Decision request must contain a certificate when overallDecision is GRANTED"));
  }

  @ParameterizedTest
  @MethodSource("invalidRequestCases")
  public void givenInvalidMakeDecisionRequest_whenMakeDecision_thenReturnBadRequest(
      Function<ProceedingEntity, MakeDecisionRequest> requestFactory, String errorTemplate)
      throws Exception {

    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
              builder.caseworker(CaseworkerJohnDoe);
            });

    ProceedingEntity proceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    MakeDecisionRequest makeDecisionRequest = requestFactory.apply(proceedingEntity);
    String expectedError =
        errorTemplate.contains("%s")
            ? String.format(errorTemplate, proceedingEntity.getId())
            : errorTemplate;

    // when
    HarnessResult result =
        patchUri(TestConstants.URIs.MAKE_DECISION, makeDecisionRequest, applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
    assertThat(result.getResponse().getHeader("Content-Type"))
        .startsWith("application/problem+json");

    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    Assertions.assertThat(problemDetail).isNotNull();
    Assertions.assertThat(problemDetail.getProperties()).isNotNull();
    @SuppressWarnings("unchecked")
    List<String> errors = (List<String>) problemDetail.getProperties().get("errors");
    Assertions.assertThat(errors).contains(expectedError);
  }

  @Test
  public void givenWrongApplicationVersion_whenMakeDecision_thenReturnConflict() throws Exception {
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              builder.caseworker(CaseworkerJohnDoe);
            });

    ProceedingEntity proceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder ->
                builder
                    .applicationVersion(99L)
                    .overallDecision(DecisionStatus.REFUSED)
                    .autoGranted(false)
                    .eventHistory(
                        EventHistoryRequest.builder().eventDescription("refusal event").build())
                    .proceedings(
                        List.of(
                            createMakeDecisionProceeding(
                                proceedingEntity.getId(),
                                MeritsDecisionStatus.REFUSED,
                                "justification",
                                "reason"))));

    // when
    HarnessResult result =
        patchUri(TestConstants.URIs.MAKE_DECISION, makeDecisionRequest, applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertEquals(HttpStatus.CONFLICT.value(), result.getResponse().getStatus());
    assertThat(result.getResponse().getHeader("Content-Type"))
        .startsWith("application/problem+json");
  }

  @Test
  public void
      givenGrantedDecisionWithCertificate_whenMakeDecision_thenReturnNoContent_andDecisionAndCertificateSaved()
          throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
              builder.caseworker(CaseworkerJohnDoe);
            });

    ProceedingEntity proceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    CertificateContent expectedCertificateContent =
        DataGenerator.createDefault(CertificateContentGenerator.class);

    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder -> {
              builder
                  .eventHistory(
                      EventHistoryRequest.builder().eventDescription("granted event").build())
                  .overallDecision(DecisionStatus.GRANTED)
                  .proceedings(
                      List.of(
                          createMakeDecisionProceeding(
                              proceedingEntity.getId(),
                              MeritsDecisionStatus.GRANTED,
                              "justification 1",
                              "reason 1")))
                  .certificate(objectMapper.convertValue(expectedCertificateContent, Map.class))
                  .autoGranted(false);
            });

    // when
    HarnessResult result =
        patchUri(TestConstants.URIs.MAKE_DECISION, makeDecisionRequest, applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);

    ApplicationEntity updatedApplicationEntity =
        applicationRepository.findById(applicationEntity.getId()).orElseThrow();
    assertEquals(ApplicationStatus.APPLICATION_SUBMITTED, updatedApplicationEntity.getStatus());
    assertEquals(false, updatedApplicationEntity.getIsAutoGranted());
    assertThat(decisionRepository.countById(updatedApplicationEntity.getDecision().getId()))
        .isEqualTo(1);

    verifyDecisionSavedCorrectly(applicationEntity.getId(), makeDecisionRequest);

    verifyCertificateSavedCorrectly(applicationEntity.getId());
  }

  @Test
  public void
      givenGrantedDecisionCalledTwice_whenAssignDecision_thenCertificateIsUpdatedNotDuplicated()
          throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
              builder.caseworker(CaseworkerJohnDoe);
            });

    ProceedingEntity proceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    CertificateContent originalCertificateContent =
        DataGenerator.createDefault(CertificateContentGenerator.class);

    MakeDecisionRequest firstMakeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder -> {
              builder
                  .eventHistory(
                      EventHistoryRequest.builder().eventDescription("granted event").build())
                  .overallDecision(DecisionStatus.GRANTED)
                  .proceedings(
                      List.of(
                          createMakeDecisionProceeding(
                              proceedingEntity.getId(),
                              MeritsDecisionStatus.GRANTED,
                              "justification 1",
                              "reason 1")))
                  .certificate(objectMapper.convertValue(originalCertificateContent, Map.class))
                  .autoGranted(false);
            });

    // First call - creates the certificate
    HarnessResult firstResult =
        patchUri(
            TestConstants.URIs.MAKE_DECISION, firstMakeDecisionRequest, applicationEntity.getId());
    assertNoContent(firstResult);

    List<CertificateEntity> certificatesAfterFirst = certificateRepository.findAll();
    assertThat(certificatesAfterFirst.size()).isEqualTo(1);
    UUID originalCertificateId = certificatesAfterFirst.get(0).getId();

    // Updated certificate content using generator with customised values
    CertificateContent updatedCertificateContent =
        DataGenerator.createDefault(
            CertificateContentGenerator.class,
            builder ->
                builder
                    .certificateNumber("UPDATEDCERT002")
                    .issueDate("2026-06-01")
                    .validUntil("2027-06-01"));
    Map<String, Object> updatedCertificateData =
        objectMapper.convertValue(updatedCertificateContent, Map.class);

    ApplicationEntity refreshedApplication =
        applicationRepository.findById(applicationEntity.getId()).orElseThrow();
    Long currentVersion = refreshedApplication.getVersion();

    MakeDecisionRequest secondMakeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder -> {
              builder
                  .eventHistory(
                      EventHistoryRequest.builder()
                          .eventDescription("granted event updated")
                          .build())
                  .overallDecision(DecisionStatus.GRANTED)
                  .proceedings(
                      List.of(
                          createMakeDecisionProceeding(
                              proceedingEntity.getId(),
                              MeritsDecisionStatus.GRANTED,
                              "justification 2",
                              "reason 2")))
                  .certificate(updatedCertificateData)
                  .applicationVersion(currentVersion)
                  .autoGranted(false);
            });

    // Second call - should update the existing certificate
    HarnessResult secondResult =
        patchUri(
            TestConstants.URIs.MAKE_DECISION, secondMakeDecisionRequest, applicationEntity.getId());

    // then
    assertNoContent(secondResult);

    // Verify only one certificate exists (updated, not duplicated)
    List<CertificateEntity> certificatesAfterSecond = certificateRepository.findAll();
    assertThat(certificatesAfterSecond.size()).isEqualTo(1);

    CertificateEntity updatedCertificate = certificatesAfterSecond.get(0);
    assertThat(updatedCertificate.getId()).isEqualTo(originalCertificateId);
    assertThat(certificateRepository.existsByApplication_Id(applicationEntity.getId())).isTrue();
    assertThat(updatedCertificate.getCertificateContent().get("certificateNumber"))
        .isEqualTo(updatedCertificateContent.getCertificateNumber());
    assertThat(updatedCertificate.getCertificateContent().get("issueDate"))
        .isEqualTo(updatedCertificateContent.getIssueDate());
    assertThat(updatedCertificate.getCertificateContent().get("validUntil"))
        .isEqualTo(updatedCertificateContent.getValidUntil());
    // updatedBy logic will be reverted once security is in place
    // assertThat(updatedCertificate.getUpdatedBy()).isEqualTo(CaseworkerJohnDoe.getId().toString());
  }

  private void verifyCertificateSavedCorrectly(UUID applicationId) {
    List<CertificateEntity> certificates = certificateRepository.findAll();
    assertThat(certificates.size()).isEqualTo(1);

    CertificateEntity certificate = certificates.get(0);
    assertThat(certificateRepository.existsByApplication_Id(applicationId)).isTrue();
    assertThat(certificate.getCertificateContent()).isNotNull();
    assertThat(certificate.getCertificateContent().get("certificateNumber"))
        .isEqualTo("TESTCERT001");
    assertThat(certificate.getCertificateContent().get("issueDate")).isEqualTo("2026-03-03");
    assertThat(certificate.getCertificateContent().get("validUntil")).isEqualTo("2027-03-03");
    // createdBy and updatedBy logic will be reverted once security is in place
    // assertThat(certificate.getCreatedBy()).isEqualTo(CaseworkerJohnDoe.getId().toString());
    // assertThat(certificate.getUpdatedBy()).isEqualTo(CaseworkerJohnDoe.getId().toString());
    assertThat(certificate.getCreatedAt()).isNotNull();
    assertThat(certificate.getModifiedAt()).isNotNull();
  }

  @Test
  public void givenRefusedDecisionWithNoCertificate_whenMakeDecision_thenReturnNoContent()
      throws Exception {
    // given
    ApplicationEntity applicationEntity =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> {
              builder.applicationContent(new HashMap<>(Map.of("test", "content")));
              builder.status(ApplicationStatus.APPLICATION_SUBMITTED);
              builder.caseworker(CaseworkerJohnDoe);
            });

    ProceedingEntity proceedingEntity =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class, builder -> builder.application(applicationEntity));

    MakeDecisionRequest makeDecisionRequest =
        DataGenerator.createDefault(
            ApplicationMakeDecisionRequestGenerator.class,
            builder -> {
              builder
                  .eventHistory(
                      EventHistoryRequest.builder().eventDescription("refusal event").build())
                  .overallDecision(DecisionStatus.REFUSED)
                  .proceedings(
                      List.of(
                          createMakeDecisionProceeding(
                              proceedingEntity.getId(),
                              MeritsDecisionStatus.REFUSED,
                              "justification 1",
                              "reason 1")))
                  .certificate(null)
                  .autoGranted(false);
            });

    // when
    HarnessResult result =
        patchUri(TestConstants.URIs.MAKE_DECISION, makeDecisionRequest, applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);

    // Verify no certificate was persisted
    List<CertificateEntity> certificates = certificateRepository.findAll();
    assertThat(certificates.size()).isEqualTo(0);
  }
}
