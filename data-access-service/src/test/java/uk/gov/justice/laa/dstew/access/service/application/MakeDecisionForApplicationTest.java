package uk.gov.justice.laa.dstew.access.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.service.application.sharedAsserts.DomainEvent.verifyThatDomainEventSaved;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import tools.jackson.core.JacksonException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationMakeDecisionRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionDetailsGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.MakeDecisionProceedingGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.testDto.certificate.CertificateContent;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Unit tests for the make decision behaviour in the application service.
 */
public class MakeDecisionForApplicationTest extends BaseServiceTest {

  @Autowired
  private ApplicationService serviceUnderTest;

  static Stream<Arguments> validationFailureCases() {
    return Stream.of(
        Arguments.of(
            (Function<UUID, MakeDecisionRequest>) ignoredId ->
                DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, b -> b
                    .overallDecision(DecisionStatus.REFUSED)
                    .eventHistory(EventHistoryRequest.builder().eventDescription("event").build())
                    .proceedings(List.of())),
            (Function<UUID, String>) id -> "The Make Decision request must contain at least one proceeding"
        ),
        Arguments.of(
            (Function<UUID, MakeDecisionRequest>) proceedingId ->
                DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, b -> b
                    .overallDecision(DecisionStatus.GRANTED)
                    .eventHistory(EventHistoryRequest.builder().eventDescription("granted event").build())
                    .certificate(null)
                    .proceedings(List.of(
                        createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.GRANTED, "justification", "reason")))),
            (Function<UUID, String>) id -> "The Make Decision request must contain a certificate when overallDecision is GRANTED"
        ),
        Arguments.of(
            (Function<UUID, MakeDecisionRequest>) proceedingId ->
                DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, b -> b
                    .overallDecision(DecisionStatus.GRANTED)
                    .eventHistory(EventHistoryRequest.builder().eventDescription("granted event").build())
                    .certificate(Map.of())
                    .proceedings(List.of(
                        createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.GRANTED, "justification", "reason")))),
            (Function<UUID, String>) id -> "The Make Decision request must contain a certificate when overallDecision is GRANTED"
        ),
        Arguments.of(
            (Function<UUID, MakeDecisionRequest>) proceedingId ->
                DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, b -> b
                    .overallDecision(DecisionStatus.REFUSED)
                    .eventHistory(EventHistoryRequest.builder().eventDescription("event").build())
                    .proceedings(List.of(
                        createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.REFUSED, "reason", "")))),
            (Function<UUID, String>) id ->
                "The Make Decision request must contain a refusal justification for proceeding with id: " + id
        )
    );
  }

  @ParameterizedTest
  @MethodSource("validationFailureCases")
  void givenInvalidMakeDecisionRequest_whenMakeDecision_thenValidationExceptionThrown(
      Function<UUID, MakeDecisionRequest> requestFactory,
      Function<UUID, String> expectedErrorFactory) {

    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);
    ApplicationEntity applicationEntity = getApplicationEntity(applicationId, caseworker, "content");

    setSecurityContext(TestConstants.Roles.CASEWORKER);
    when(applicationRepository.findByIdWithAssociations(applicationId)).thenReturn(Optional.of(applicationEntity));

    MakeDecisionRequest request = requestFactory.apply(proceedingId);
    String expectedError = expectedErrorFactory.apply(proceedingId);

    Throwable thrown = catchThrowable(() -> serviceUnderTest.makeDecision(applicationId, request));

    assertThat(thrown).isInstanceOf(ValidationException.class);
    assertThat(((ValidationException) thrown).errors()).contains(expectedError);
    verify(applicationRepository, never()).save(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenMakeDecisionRequestWithTwoProceedings_whenAssignDecision_thenDecisionSaved(boolean certificateExists) throws
      JacksonException {
    UUID applicationId = UUID.randomUUID();
    UUID grantedProceedingId = UUID.randomUUID();
    UUID refusedProceedingId = UUID.randomUUID();

    MeritsDecisionStatus grantedDecision = MeritsDecisionStatus.GRANTED;
    String grantedReason = "refusal 1";
    String grantedJustification = "justification 1";

    MeritsDecisionStatus refusedDecision = MeritsDecisionStatus.REFUSED;
    String refusedReason = "refusal 2";
    String refusedJustification = "justification 2";

    // given
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    // overwrite some fields of default assign decision request
    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .overallDecision(DecisionStatus.REFUSED)
            .eventHistory(
                EventHistoryRequest.builder()
                    .eventDescription("event")
                    .build()
            )
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(grantedProceedingId, grantedDecision, grantedReason, grantedJustification),
                createMakeDecisionProceedingDetails(refusedProceedingId, refusedDecision, refusedReason, refusedJustification)
            ))
    );

    // expected saved application entity
    final ApplicationEntity expectedApplicationEntity =
        getApplicationEntity(applicationId, caseworker, "unmodified");

    final DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
        .applicationId(applicationId)
        .caseworkerId(caseworker.getId())
        .createdBy("")
        .type(DomainEventType.APPLICATION_MAKE_DECISION_REFUSED)
        .data(objectMapper.writeValueAsString(
            MakeDecisionDomainEventDetails.builder()
                .applicationId(applicationId)
                .caseworkerId(caseworker.getId())
                .eventDescription("event")
                .request(objectMapper.writeValueAsString(makeDecisionRequest))
                .build()
        ))
        .build();

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    ProceedingEntity grantedProceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
        builder.id(grantedProceedingId).applicationId(applicationId)
    );
    ProceedingEntity refusedProceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
        builder.id(refusedProceedingId).applicationId(applicationId)
    );

    // when
    when(certificateRepository.existsByApplicationId(applicationId)).thenReturn(certificateExists);
    when(proceedingRepository.findAllById(List.of(grantedProceedingEntity.getId(), refusedProceedingEntity.getId())))
            .thenReturn(List.of(grantedProceedingEntity, refusedProceedingEntity));
    when(applicationRepository.findByIdWithAssociations(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

    // then
    verify(applicationRepository, times(1)).findByIdWithAssociations(expectedApplicationEntity.getId());
    verify(applicationRepository, times(2)).save(any(ApplicationEntity.class));
    verify(domainEventRepository, times(1)).save(any(DomainEventEntity.class));
    verify(certificateRepository, times((certificateExists)?1:0)).deleteByApplicationId(applicationId);
    verifyThatDomainEventSaved(domainEventRepository, objectMapper, expectedDomainEvent, 1);
    verifyDecisionSavedCorrectly(makeDecisionRequest,
                    expectedApplicationEntity,
        2);
  }

  @Test
  void givenApplicationAndExistingDecision_whenAssignDecision_thenDecisionUpdated() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();

    // given
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    final MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .eventHistory(
                EventHistoryRequest.builder()
                    .eventDescription("event")
                    .build()
            )
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(proceedingId,
                                                    MeritsDecisionStatus.GRANTED,
                                                    "refusal update",
                                                    "justification update")
            ))
    );

    // expected saved application entity
    final ApplicationEntity expectedApplicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
        builder
            .id(applicationId)
            .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
            .caseworker(caseworker)
            .version(0L)
    );

    final DecisionEntity currentSavedDecisionEntity = createDecisionEntityWithProceeding(
        proceedingId,
        MeritsDecisionStatus.REFUSED,
        "initial reason",
        "initial justification", DecisionStatus.REFUSED
    );

    expectedApplicationEntity.setDecision(currentSavedDecisionEntity);

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    ProceedingEntity proceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
        builder.id(proceedingId).applicationId(applicationId)
    );

    // when
    when(proceedingRepository.findAllById(List.of(proceedingId))).thenReturn(List.of(proceedingEntity));
    when(applicationRepository.findByIdWithAssociations(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

    // then
    verify(applicationRepository, times(1)).findByIdWithAssociations(expectedApplicationEntity.getId());
    verify(applicationRepository, times(1)).save(any(ApplicationEntity.class));
    verify(domainEventRepository).save(any(DomainEventEntity.class));

    verifyDecisionSavedCorrectly(makeDecisionRequest, expectedApplicationEntity, 1);
  }

  @Test
  void givenApplicationAndExistingDecisionAndNewProceeding_whenAssignDecisionGranted_thenDecisionUpdated() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    UUID newProceedingId = UUID.randomUUID();

    // given
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .overallDecision(DecisionStatus.GRANTED)
            .eventHistory(
                EventHistoryRequest.builder()
                    .eventDescription(null)
                    .build()
            )
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(newProceedingId,
                                                    MeritsDecisionStatus.GRANTED,
                                                    "initial reason",
                                                    "new justification")
            )).certificate(Map.of("Test Certificate", "test value") )
    );

    // expected saved application entity
    final ApplicationEntity expectedApplicationEntity = getApplicationEntity(applicationId, caseworker, "unmodified");

    final DecisionEntity currentSavedDecisionEntity = createDecisionEntityWithProceeding(
        proceedingId,
        MeritsDecisionStatus.GRANTED,
        "initial reason",
        "initial justification", DecisionStatus.GRANTED
    );

    expectedApplicationEntity.setDecision(currentSavedDecisionEntity);

    final DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
        .applicationId(applicationId)
        .caseworkerId(caseworker.getId())
        .createdBy("")
        .type(DomainEventType.APPLICATION_MAKE_DECISION_GRANTED)
        .data(objectMapper.writeValueAsString(
            MakeDecisionDomainEventDetails.builder()
                .applicationId(applicationId)
                .caseworkerId(caseworker.getId())
                .eventDescription(null)
                .request(objectMapper.writeValueAsString(makeDecisionRequest))
                .build()
        ))
        .build();

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    ProceedingEntity newProceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
                builder.id(newProceedingId).applicationId(applicationId)
                );

    // when
    when(proceedingRepository.findAllById(List.of(newProceedingId))).thenReturn(List.of(newProceedingEntity));
    when(applicationRepository.findByIdWithAssociations(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

    // then
    verify(applicationRepository, times(1)).findByIdWithAssociations(expectedApplicationEntity.getId());
    verify(applicationRepository, times(1)).save(any(ApplicationEntity.class));
    verify(domainEventRepository, times(1)).save(any(DomainEventEntity.class));
    verifyThatDomainEventSaved(domainEventRepository, objectMapper, expectedDomainEvent, 1);
    verifyDecisionSavedCorrectly(makeDecisionRequest, expectedApplicationEntity, 2);
  }

  @Test
  void givenApplicationAndDecisionRequestWithMultipleProceedingsAndExistingDecision_whenAssignDecision_thenDecisionUpdated() {
    UUID applicationId = UUID.randomUUID();
    UUID currentProceedingId = UUID.randomUUID();
    UUID newProceedingId = UUID.randomUUID();

    // given
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    final MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .eventHistory(EventHistoryRequest.builder().build())
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(newProceedingId,
                                                    MeritsDecisionStatus.REFUSED,
                                                    "refusal new",
                                                    "justification new"),
                createMakeDecisionProceedingDetails(currentProceedingId,
                                                    MeritsDecisionStatus.GRANTED,
                                                    "refusal update",
                                                    "justification update")
            ))
    );

    // expected saved application entity
    final ApplicationEntity expectedApplicationEntity = getApplicationEntity(applicationId, caseworker, "unmodified");

    final DecisionEntity currentSavedDecisionEntity = createDecisionEntityWithProceeding(
        currentProceedingId,
        MeritsDecisionStatus.REFUSED,
        "current reason",
        "current justification", DecisionStatus.REFUSED
    );

    expectedApplicationEntity.setDecision(currentSavedDecisionEntity);

    ProceedingEntity currentProceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
                builder.id(currentProceedingId).applicationId(applicationId)
                );

    ProceedingEntity newProceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
                builder.id(newProceedingId).applicationId(applicationId)
                );

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    when(proceedingRepository.findAllById(List.of(newProceedingId, currentProceedingId)))
                .thenReturn(List.of(newProceedingEntity, currentProceedingEntity));
    when(applicationRepository.findByIdWithAssociations(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

    // then
    verify(applicationRepository, times(1)).findByIdWithAssociations(expectedApplicationEntity.getId());
    verify(applicationRepository, times(1)).save(any(ApplicationEntity.class));
    verify(domainEventRepository).save(any(DomainEventEntity.class));
    verifyDecisionSavedCorrectly(makeDecisionRequest, expectedApplicationEntity, 2);
  }

  private static ApplicationEntity getApplicationEntity(UUID applicationId, CaseworkerEntity caseworker, String content) {
    return DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
        builder
            .id(applicationId)
            .applicationContent(new HashMap<>(Map.of("test", content)))
            .caseworker(caseworker)
            .version(0L)
    );
  }

  private static ApplicationEntity getApplicationEntity(UUID applicationId, String content) {
    return DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
        builder
            .id(applicationId)
            .applicationContent(new HashMap<>(Map.of("test", content)))
            .version(0L)
    );
  }

  @Test
  @Disabled("Temporarily disabled until security is implemented")
  void givenApplicationWithNoCaseworker_whenAssignDecision_thenThrowResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();

    // given
    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class);

    // expected saved application entity
    ApplicationEntity expectedApplicationEntity = getApplicationEntity(applicationId, "unmodified");

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(applicationRepository.findByIdWithAssociations(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    Throwable thrown = catchThrowable(() ->
        serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest));

    assertThat(thrown)
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Caseworker not found for application id: " + applicationId);

    verify(applicationRepository, never()).save(any());


  }

  @Test
  void givenNoApplication_whenAssignDecision_thenThrowResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();

    // given
    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class);

    // expected saved application entity
    ApplicationEntity expectedApplicationEntity = getApplicationEntity(applicationId, "unmodified");

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    Throwable thrown = catchThrowable(() ->
        serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest));

    assertThat(thrown)
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No application found with id: " + applicationId);

    verify(applicationRepository, never()).save(any());

  }
  static Stream<Arguments> notFoundScenarios() {
    UUID appId1 = UUID.randomUUID();
    UUID appId2 = UUID.randomUUID();
    UUID appId3 = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    return Stream.of(
        Arguments.of("NO_APPLICATION", appId1, proceedingId, "No application found with id: " + appId1),
        // Note: the NO_CASEWORKER scenario is only relevant until security is implemented and can be removed afterwards,
        // but it is currently a separate test to allow for clearer assertions on the exception message
        // Arguments.of("NO_CASEWORKER",  appId2, proceedingId, "Caseworker not found for application id: " + appId2),
        Arguments.of("NO_PROCEEDING",  appId3, proceedingId, "No proceeding found with id: " + proceedingId)
    );
  }


  @ParameterizedTest(name = "{0}")
  @MethodSource("notFoundScenarios")
  void givenMissingEntity_whenMakeDecision_thenThrowResourceNotFoundException(
      String scenario, UUID applicationId, UUID proceedingId, String expectedMessage) {

    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);
    MakeDecisionRequest request = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, b -> b
        .proceedings(List.of(
            createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.GRANTED, "reason", "justification")
        ))
    );

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    switch (scenario) {
      case "NO_CASEWORKER" -> when(applicationRepository.findByIdWithAssociations(applicationId))
          .thenReturn(Optional.of(getApplicationEntity(applicationId, "content")));
      case "NO_PROCEEDING" -> when(applicationRepository.findByIdWithAssociations(applicationId))
          .thenReturn(Optional.of(getApplicationEntity(applicationId, caseworker, "content")));
      // NO_APPLICATION: no mock → findById returns empty Optional by default
    }

    Throwable thrown = catchThrowable(() -> serviceUnderTest.makeDecision(applicationId, request));

    assertThat(thrown)
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage(expectedMessage);
    // save() is called before proceeding lookup, so only assert never-saved for early-exit scenarios
    if (!scenario.equals("NO_PROCEEDING")) {
      verify(applicationRepository, never()).save(any());
    }
  }

  @Test
  void givenProceedingNotLinkedToApplication_whenMakeDecision_thenThrowResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    UUID unrelatedApplicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();

    // given
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    final MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
            requestBuilder
                .proceedings(List.of(
                    createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.GRANTED, "reason", "justification")
                ))
        );

    ApplicationEntity expectedApplicationEntity = getApplicationEntity(applicationId, caseworker, "unmodified");

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    ProceedingEntity proceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder -> builder.id(proceedingId).applicationId(unrelatedApplicationId));

    // when
    when(applicationRepository.findByIdWithAssociations(applicationId)).thenReturn(Optional.of(expectedApplicationEntity));
    when(proceedingRepository.findAllById(List.of(proceedingId))).thenReturn(List.of(proceedingEntity));

    Throwable thrown = catchThrowable(
            () -> serviceUnderTest.makeDecision(applicationId, makeDecisionRequest)
        );

    // then
    assertThat(thrown)
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Not linked to application: " + proceedingId);
  }

  @Test
  void givenProceedingsNotFoundAndNotLinkedToApplication_whenMakeDecision_thenThrowResourceNotFoundExceptionWithAllIds() {
    UUID applicationId = UUID.randomUUID();
    UUID unrelatedApplicationId = UUID.randomUUID();
    UUID nonExistentProceedingId = UUID.randomUUID();
    UUID unrelatedApplicationProceedingId = UUID.randomUUID();

    // given
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    final MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
            requestBuilder
                .proceedings(List.of(
                    createMakeDecisionProceedingDetails(nonExistentProceedingId,
                        MeritsDecisionStatus.GRANTED, "reason1",
                        "justification1"),
                    createMakeDecisionProceedingDetails(unrelatedApplicationProceedingId,
                        MeritsDecisionStatus.REFUSED, "reason2",
                        "justification2")
                ))
        );

    ApplicationEntity expectedApplicationEntity =
        getApplicationEntity(applicationId, caseworker, "unmodified");

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    ProceedingEntity unrelatedApplicationProceeding = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder -> builder.id(unrelatedApplicationProceedingId).applicationId(unrelatedApplicationId));

    // when
    when(applicationRepository.findByIdWithAssociations(applicationId)).thenReturn(Optional.of(expectedApplicationEntity));
    when(proceedingRepository.findAllById(List.of(nonExistentProceedingId, unrelatedApplicationProceedingId)))
        .thenReturn(List.of(unrelatedApplicationProceeding));

    Throwable thrown = catchThrowable(
            () -> serviceUnderTest.makeDecision(applicationId, makeDecisionRequest)
        );

    // then
    assertThat(thrown)
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("No proceeding found with id:")
            .hasMessageContaining(nonExistentProceedingId.toString())
            .hasMessageContaining("Not linked to application:")
            .hasMessageContaining(unrelatedApplicationProceedingId.toString());
  }

  @Test
  void givenGrantedDecisionWithCertificate_whenMakeDecision_thenCertificateSaved() {
    // given
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder ->
        builder.id(caseworkerId)
    );

    CertificateContent certificateContent = DataGenerator.createDefault(CertificateContentGenerator.class);
    Map<String, Object> certificateData = objectMapper.convertValue(certificateContent, Map.class);

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .overallDecision(DecisionStatus.GRANTED)
            .eventHistory(EventHistoryRequest.builder()
                .eventDescription("granted event")
                .build())
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.GRANTED, "justification", "reason")
            ))
            .certificate(certificateData)
    );

    ApplicationEntity applicationEntity = getApplicationEntity(applicationId, caseworker, "content");

    ProceedingEntity proceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
        builder.id(proceedingId).applicationId(applicationId)
    );

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(proceedingRepository.findAllById(List.of(proceedingId))).thenReturn(List.of(proceedingEntity));
    when(applicationRepository.findByIdWithAssociations(applicationId)).thenReturn(Optional.of(applicationEntity));
    when(certificateRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());

    // when
    serviceUnderTest.makeDecision(applicationId, makeDecisionRequest);

    // then
    ArgumentCaptor<CertificateEntity> certificateCaptor =
        ArgumentCaptor.forClass(CertificateEntity.class);
    verify(certificateRepository, times(1)).save(certificateCaptor.capture());

    CertificateEntity savedCertificate = certificateCaptor.getValue();
    assertThat(savedCertificate.getApplicationId()).isEqualTo(applicationId);
    assertThat(savedCertificate.getCertificateContent()).isEqualTo(certificateData);
    // createdBy and updatedBy logic will be reverted once security is in place
    // assertThat(savedCertificate.getCreatedBy()).isEqualTo(caseworkerId.toString());
    // assertThat(savedCertificate.getUpdatedBy()).isEqualTo(caseworkerId.toString());

    ArgumentCaptor<DomainEventEntity> domainEventCaptor = ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository, times(1)).save(domainEventCaptor.capture());
    assertThat(domainEventCaptor.getValue()).isInstanceOf(DomainEventEntity.class);
    DomainEventEntity domainEventEntityCaptured = domainEventCaptor.getValue();
    assertThat(domainEventEntityCaptured.getApplicationId()).isEqualTo(applicationId);
    assertThat(domainEventEntityCaptured.getCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(domainEventEntityCaptured.getType()).isEqualTo(DomainEventType.APPLICATION_MAKE_DECISION_GRANTED);

    verify(applicationRepository, times(1)).findByIdWithAssociations(applicationId);
    verify(applicationRepository, times(2)).save(any(ApplicationEntity.class));
  }

  @Test
  void givenGrantedDecisionWithExistingCertificate_whenMakeDecision_thenCertificateUpdated() {
    // given
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    UUID existingCertificateId = UUID.randomUUID();
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder ->
        builder.id(caseworkerId)
    );

    CertificateContent certificateContent = DataGenerator.createDefault(CertificateContentGenerator.class);
    Map<String, Object> certificateData = objectMapper.convertValue(certificateContent, Map.class);

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .overallDecision(DecisionStatus.GRANTED)
            .eventHistory(EventHistoryRequest.builder()
                .eventDescription("granted event")
                .build())
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.GRANTED, "justification", "reason")
            ))
            .certificate(certificateData)
    );

    ApplicationEntity applicationEntity = getApplicationEntity(applicationId, caseworker, "content");

    ProceedingEntity proceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
        builder.id(proceedingId).applicationId(applicationId)
    );

    uk.gov.justice.laa.dstew.access.entity.CertificateEntity existingCertificate =
        uk.gov.justice.laa.dstew.access.entity.CertificateEntity.builder()
            .id(existingCertificateId)
            .applicationId(applicationId)
            .certificateContent(Map.of("old", "content"))
            .createdBy("original-caseworker")
            .updatedBy("original-caseworker")
            .build();

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(proceedingRepository.findAllById(List.of(proceedingId))).thenReturn(List.of(proceedingEntity));
    when(applicationRepository.findByIdWithAssociations(applicationId)).thenReturn(Optional.of(applicationEntity));
    when(certificateRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(existingCertificate));

    // when
    serviceUnderTest.makeDecision(applicationId, makeDecisionRequest);

    // then
    ArgumentCaptor<uk.gov.justice.laa.dstew.access.entity.CertificateEntity> certificateCaptor =
        ArgumentCaptor.forClass(uk.gov.justice.laa.dstew.access.entity.CertificateEntity.class);
    verify(certificateRepository, times(1)).save(certificateCaptor.capture());

    uk.gov.justice.laa.dstew.access.entity.CertificateEntity savedCertificate = certificateCaptor.getValue();
    assertThat(savedCertificate.getId()).isEqualTo(existingCertificateId);
    assertThat(savedCertificate.getApplicationId()).isEqualTo(applicationId);
    assertThat(savedCertificate.getCertificateContent()).isEqualTo(certificateData);
    assertThat(savedCertificate.getCreatedBy()).isEqualTo("original-caseworker");
    // updatedBy logic will be reverted once security is in place
    // assertThat(savedCertificate.getUpdatedBy()).isEqualTo(caseworkerId.toString());
  }

  @Test
  void givenRefusedDecisionWithoutCertificate_whenMakeDecision_thenNoCertificateSaved() {
    // given
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .overallDecision(DecisionStatus.REFUSED)
            .eventHistory(EventHistoryRequest.builder()
                .eventDescription("refusal event")
                .build())
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.REFUSED, "justification", "reason")
            ))
            .certificate(null)
    );

    ApplicationEntity applicationEntity = getApplicationEntity(applicationId, caseworker, "content");

    ProceedingEntity proceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
        builder.id(proceedingId).applicationId(applicationId)
    );

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(proceedingRepository.findAllById(List.of(proceedingId))).thenReturn(List.of(proceedingEntity));
    when(applicationRepository.findByIdWithAssociations(applicationId)).thenReturn(Optional.of(applicationEntity));

    // when
    serviceUnderTest.makeDecision(applicationId, makeDecisionRequest);

    // then
    verify(certificateRepository, never()).save(any());
    verify(applicationRepository, times(1)).findByIdWithAssociations(applicationId);
    verify(applicationRepository, times(2)).save(any(ApplicationEntity.class));
    verify(domainEventRepository, times(1)).save(any(DomainEventEntity.class));
  }

  @Test
  void givenDecisionWithIncorrectVersionThenThrowException() {
    // given
    UUID applicationId = UUID.randomUUID();

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .applicationVersion(1L) // version should be 0 for the first decision
    );

    ApplicationEntity applicationEntity = getApplicationEntity(applicationId, "content");

    when(applicationRepository.findByIdWithAssociations(applicationId)).thenReturn(Optional.of(applicationEntity));

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    Throwable thrown = catchThrowable(() ->
        serviceUnderTest.makeDecision(applicationId, makeDecisionRequest)
    );

    // then
    assertThat(thrown).isInstanceOf(OptimisticLockingFailureException.class);
    assertThat(thrown.getMessage())
        .contains("version 1 not found");

    verify(meritsDecisionRepository, never()).save(any());
    verify(applicationRepository, times(1)).findByIdWithAssociations(applicationId);
    verify(applicationRepository, never()).save(any());
  }

  private DecisionEntity createDecisionEntityWithProceeding(
      UUID proceedingId,
      MeritsDecisionStatus decision,
      String reason,
      String justification, DecisionStatus decisionStatus) {
        return DataGenerator.createDefault(DecisionEntityGenerator.class, builder ->
            builder.overallDecision(decisionStatus)
                    .meritsDecisions(new HashSet<>(Set.of(
                            DataGenerator.createDefault(MeritsDecisionsEntityGenerator.class, mBuilder ->
                                    mBuilder
                                            .proceeding(ProceedingEntity.builder().id(proceedingId).build())
                                            .decision(decision)
                                            .reason(reason)
                                            .justification(justification)
                                            .modifiedAt(Instant.now())
                            )
                    )))
        );
    }

    private static MakeDecisionProceedingRequest createMakeDecisionProceedingDetails(UUID proceedingId,
        MeritsDecisionStatus decision, String reason, String justification) {
        return DataGenerator.createDefault(MakeDecisionProceedingGenerator.class, builder ->
            builder
                .proceedingId(proceedingId)
                .meritsDecision(
                    DataGenerator.createDefault(MeritsDecisionDetailsGenerator.class, meritsBuilder ->
                        meritsBuilder
                            .decision(decision)
                            .justification(justification)
                            .reason(reason)
                    )
                )
        );
    }

  private void verifyDecisionSavedCorrectly(
      MakeDecisionRequest expectedMakeDecisionRequest,
      ApplicationEntity expectedApplicationEntity,
      int expectedNumberOfMeritsDecisions) {

    ArgumentCaptor<DecisionEntity> decisionCaptor = ArgumentCaptor.forClass(DecisionEntity.class);
    verify(decisionRepository, times(1)).save(decisionCaptor.capture());
    DecisionEntity savedDecision = decisionCaptor.getValue();

    MakeDecisionRequest actual = mapToMakeDecisionRequest(
                    savedDecision,
                    expectedApplicationEntity,
                    expectedMakeDecisionRequest.getEventHistory());

    assertThat(actual.getProceedings().size()).isEqualTo(expectedNumberOfMeritsDecisions);

    // only check general fields, not proceedings as there may be a size discrepancy if
    // adding a new merits decision.
    assertThat(actual)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .ignoringFields("applicationVersion")
        .ignoringFields("proceedings")
        .ignoringFields("certificate")
        .isEqualTo(expectedMakeDecisionRequest);

    // Assert only on the set of proceedings that are updated or added via the request.
    Set<UUID> expectedProceedingIds = expectedMakeDecisionRequest.getProceedings().stream()
        .map(MakeDecisionProceedingRequest::getProceedingId)
        .collect(Collectors.toSet());
    List<MakeDecisionProceedingRequest> actualProceedings = actual.getProceedings().stream()
        .filter(p -> expectedProceedingIds.contains(p.getProceedingId()))
        .collect(Collectors.toList());
    assertThat(actualProceedings)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedMakeDecisionRequest.getProceedings());

    assertThat(savedDecision.getModifiedAt()).isNotNull();
    assertThat(savedDecision.getMeritsDecisions())
        .allSatisfy(merits -> assertThat(merits.getModifiedAt()).isNotNull());
  }

  // DecisionEntity -> MakeDecisionRequest
  private static MakeDecisionRequest mapToMakeDecisionRequest(
      DecisionEntity decisionEntity,
      ApplicationEntity applicationEntity,
      EventHistoryRequest eventHistoryRequest) {
    if (decisionEntity == null) {
      return null;
    }
    return MakeDecisionRequest.builder()
        .overallDecision(decisionEntity.getOverallDecision())
        .eventHistory(eventHistoryRequest)
        .proceedings(decisionEntity.getMeritsDecisions().stream()
            .map(MakeDecisionForApplicationTest::mapToProceedingDetails)
            .toList())
        .build();
  }

  // MeritsDecisionEntity -> ProceedingDetails
  private static MakeDecisionProceedingRequest mapToProceedingDetails(MeritsDecisionEntity meritsDecisionEntity) {
    if (meritsDecisionEntity == null) {
      return null;
    }
    return MakeDecisionProceedingRequest.builder()
        .proceedingId(meritsDecisionEntity.getProceeding().getId())
        .meritsDecision(mapToMeritsDecisionDetails(meritsDecisionEntity))
        .build();
  }

  // MeritsDecisionEntity -> MeritsDecisionDetails
  private static MeritsDecisionDetailsRequest mapToMeritsDecisionDetails(MeritsDecisionEntity entity) {
    if (entity == null) {
      return null;
    }
    return MeritsDecisionDetailsRequest.builder()
        .decision(entity.getDecision())
        .justification(entity.getJustification())
        .reason(entity.getReason())
        .build();
  }
}
