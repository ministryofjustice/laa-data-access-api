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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import tools.jackson.core.JacksonException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRefusedDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetails;
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

  @Test
  void givenMakeDecisionRequestWithOneProceedingAndInvalidRefusal_whenAssignDecision_thenDecisionSaved() {

    UUID applicationId = UUID.randomUUID();
    UUID refusedProceedingId = UUID.randomUUID();

    MeritsDecisionStatus refusedDecision = MeritsDecisionStatus.GRANTED;

    // given
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
            requestBuilder
                    .overallDecision(DecisionStatus.REFUSED)
                    .eventHistory(
                            EventHistory.builder()
                                    .eventDescription("event")
                                    .build()
                    )
                    .proceedings(List.of(
                            createMakeDecisionProceedingDetails(refusedProceedingId, refusedDecision, "refusal 1", "")
                    ))
    );

    final ApplicationEntity expectedApplicationEntity = getApplicationEntity(applicationId, caseworker, "unmodified");

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    ProceedingEntity refusedProceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
            builder.id(refusedProceedingId).applicationId(applicationId)
    );

    // when
    when(proceedingRepository.findAllById(List.of(refusedProceedingEntity.getId())))
            .thenReturn(List.of(refusedProceedingEntity));
    when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    ValidationException validationException =
            Assertions.assertThrows(ValidationException.class,
                    () -> serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest));

    assertThat(validationException.getMessage()).contains("One or more validation rules were violated");

    assertThat(validationException.errors())
            .isInstanceOf(List.class)
            .contains("The Make Decision request must contain a refusal justification for proceeding with id: " + refusedProceedingId);
    verify(applicationRepository, never()).save(any());
  }

  @Test
  void givenMakeDecisionRequestWithTwoProceedings_whenAssignDecision_thenDecisionSaved() throws JacksonException {
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
                EventHistory.builder()
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
            MakeDecisionRefusedDomainEventDetails.builder()
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
    when(proceedingRepository.findAllById(List.of(grantedProceedingEntity.getId(), refusedProceedingEntity.getId())))
            .thenReturn(List.of(grantedProceedingEntity, refusedProceedingEntity));
    when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

    // then
    verify(applicationRepository, times(1)).findById(expectedApplicationEntity.getId());
    verify(applicationRepository, times(2)).save(any(ApplicationEntity.class));
    verify(domainEventRepository, times(1)).save(any(DomainEventEntity.class));
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
                EventHistory.builder()
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
    final ApplicationEntity expectedApplicationEntity =
        getApplicationEntity(applicationId, caseworker, "unmodified");

    final DecisionEntity currentSavedDecisionEntity = createDecisionEntityWithProceeding(
        proceedingId,
        MeritsDecisionStatus.REFUSED,
        "initial reason",
        "initial justification"
    );

    expectedApplicationEntity.setDecision(currentSavedDecisionEntity);

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    ProceedingEntity proceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
        builder.id(proceedingId).applicationId(applicationId)
    );

    // when
    when(proceedingRepository.findAllById(List.of(proceedingId))).thenReturn(List.of(proceedingEntity));
    when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

    // then
    verify(applicationRepository, times(1)).findById(expectedApplicationEntity.getId());
    verify(applicationRepository, times(1)).save(any(ApplicationEntity.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));

    verifyDecisionSavedCorrectly(makeDecisionRequest, expectedApplicationEntity, 1);
  }

  @Test
  void givenApplicationAndExistingDecisionAndNewProceeding_whenAssignDecision_thenDecisionUpdated()
      throws JacksonException {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    UUID newProceedingId = UUID.randomUUID();

    // given
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .overallDecision(DecisionStatus.REFUSED)
            .eventHistory(
                EventHistory.builder()
                    .eventDescription(null)
                    .build()
            )
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(newProceedingId,
                                                    MeritsDecisionStatus.GRANTED,
                                                    "new refusal",
                                                    "new justification")
            ))
    );

    // expected saved application entity
    final ApplicationEntity expectedApplicationEntity = getApplicationEntity(applicationId, caseworker, "unmodified");

    final DecisionEntity currentSavedDecisionEntity = createDecisionEntityWithProceeding(
        proceedingId,
        MeritsDecisionStatus.REFUSED,
        "initial reason",
        "initial justification"
    );

    expectedApplicationEntity.setDecision(currentSavedDecisionEntity);

    final DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
        .applicationId(applicationId)
        .caseworkerId(caseworker.getId())
        .createdBy("")
        .type(DomainEventType.APPLICATION_MAKE_DECISION_REFUSED)
        .data(objectMapper.writeValueAsString(
            MakeDecisionRefusedDomainEventDetails.builder()
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
    when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

    // then
    verify(applicationRepository, times(1)).findById(expectedApplicationEntity.getId());
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
            .eventHistory(EventHistory.builder().build())
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
        "current justification"
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
    when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

    // then
    verify(applicationRepository, times(1)).findById(expectedApplicationEntity.getId());
    verify(applicationRepository, times(1)).save(any(ApplicationEntity.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
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

    when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

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

  @Test
  void givenNoProceeding_whenAssignDecision_thenThrowResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();

    MeritsDecisionStatus decision = MeritsDecisionStatus.GRANTED;
    String reason = "refusal 1";
    String justification = "justification 1";

    // given
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    // overwrite some fields of default assign decision request
    final MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(proceedingId, decision, reason, justification)
            ))
    );

    // expected saved application entity
    ApplicationEntity expectedApplicationEntity = getApplicationEntity(applicationId, caseworker, "unmodified");

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

    Throwable thrown = catchThrowable(
        () -> serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest)
    );

    // then
    assertThat(thrown)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No proceeding found with id: " + proceedingId);
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
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(expectedApplicationEntity));
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
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(expectedApplicationEntity));
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
  void givenGrantedDecisionWithoutCertificate_whenMakeDecision_thenValidationExceptionThrown() {
    // given
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .overallDecision(DecisionStatus.GRANTED)
            .eventHistory(EventHistory.builder()
                .eventDescription("granted event")
                .build())
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.GRANTED, "justification", "reason")
            ))
            .certificate(null)
    );

    ApplicationEntity applicationEntity = getApplicationEntity(applicationId, caseworker, "content");

    ProceedingEntity proceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class, builder ->
        builder.id(proceedingId).applicationId(applicationId)
    );

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(proceedingRepository.findAllById(List.of(proceedingId))).thenReturn(List.of(proceedingEntity));
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));

    // when
    Throwable thrown = catchThrowable(() ->
        serviceUnderTest.makeDecision(applicationId, makeDecisionRequest)
    );

    // then
    assertThat(thrown).isInstanceOf(ValidationException.class);
    ValidationException validationException = (ValidationException) thrown;
    assertThat(validationException.errors())
        .contains("The Make Decision request must contain a certificate when overallDecision is GRANTED");

    verify(certificateRepository, never()).save(any());
    verify(applicationRepository, times(1)).findById(applicationId);
    verify(applicationRepository, never()).save(any());
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
            .eventHistory(EventHistory.builder()
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
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));

    // when
    serviceUnderTest.makeDecision(applicationId, makeDecisionRequest);

    // then
    ArgumentCaptor<uk.gov.justice.laa.dstew.access.entity.CertificateEntity> certificateCaptor =
        ArgumentCaptor.forClass(uk.gov.justice.laa.dstew.access.entity.CertificateEntity.class);
    verify(certificateRepository, times(1)).save(certificateCaptor.capture());

    uk.gov.justice.laa.dstew.access.entity.CertificateEntity savedCertificate = certificateCaptor.getValue();
    assertThat(savedCertificate.getApplicationId()).isEqualTo(applicationId);
    assertThat(savedCertificate.getCertificateContent()).isEqualTo(certificateData);
    // createdBy and updatedBy logic will be reverted once security is in place
    // assertThat(savedCertificate.getCreatedBy()).isEqualTo(caseworkerId.toString());
    // assertThat(savedCertificate.getUpdatedBy()).isEqualTo(caseworkerId.toString());

    verify(applicationRepository, times(1)).findById(applicationId);
    verify(applicationRepository, times(2)).save(any(ApplicationEntity.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
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
            .eventHistory(EventHistory.builder()
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
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));

    // when
    serviceUnderTest.makeDecision(applicationId, makeDecisionRequest);

    // then
    verify(certificateRepository, never()).save(any());
    verify(applicationRepository, times(1)).findById(applicationId);
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

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    Throwable thrown = catchThrowable(() ->
        serviceUnderTest.makeDecision(applicationId, makeDecisionRequest)
    );

    // then
    assertThat(thrown).isInstanceOf(OptimisticLockingFailureException.class);
    assertThat(thrown.getMessage())
        .contains("version 1 not found");

    verify(meritsDecisionRepository, never()).save(any());
    verify(applicationRepository, times(1)).findById(applicationId);
    verify(applicationRepository, never()).save(any());
  }

  private DecisionEntity createDecisionEntityWithProceeding(
          UUID proceedingId,
          MeritsDecisionStatus decision,
          String reason,
          String justification) {
        return DataGenerator.createDefault(DecisionEntityGenerator.class, builder ->
            builder.overallDecision(DecisionStatus.PARTIALLY_GRANTED)
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

    private MakeDecisionProceeding createMakeDecisionProceedingDetails(UUID proceedingId,
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
        .ignoringFields("proceedings", "applicationVersion")
        .isEqualTo(expectedMakeDecisionRequest);

    // Assert only on the set of proceedings that are updated or added via the request.
    Set<UUID> expectedProceedingIds = expectedMakeDecisionRequest.getProceedings().stream()
        .map(MakeDecisionProceeding::getProceedingId)
        .collect(Collectors.toSet());
    List<MakeDecisionProceeding> actualProceedings = actual.getProceedings().stream()
        .filter(p -> expectedProceedingIds.contains(p.getProceedingId()))
        .collect(Collectors.toList());
    assertThat(actualProceedings)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedMakeDecisionRequest.getProceedings());

    assertThat(savedDecision.getModifiedAt()).isNotNull();
    assertThat(savedDecision.getMeritsDecisions())
        .allSatisfy(merits -> {
          assertThat(merits.getModifiedAt()).isNotNull();
        });
  }

  // DecisionEntity -> MakeDecisionRequest
  private static MakeDecisionRequest mapToMakeDecisionRequest(
      DecisionEntity decisionEntity,
      ApplicationEntity applicationEntity,
      EventHistory eventHistory) {
    if (decisionEntity == null) {
      return null;
    }
    return MakeDecisionRequest.builder()
        .overallDecision(decisionEntity.getOverallDecision())
        .eventHistory(eventHistory)
        .proceedings(decisionEntity.getMeritsDecisions().stream()
            .map(MakeDecisionForApplicationTest::mapToProceedingDetails)
            .toList())
        .build();
  }

  // MeritsDecisionEntity -> ProceedingDetails
  private static MakeDecisionProceeding mapToProceedingDetails(MeritsDecisionEntity meritsDecisionEntity) {
    if (meritsDecisionEntity == null) {
      return null;
    }
    return MakeDecisionProceeding.builder()
        .proceedingId(meritsDecisionEntity.getProceeding().getId())
        .meritsDecision(mapToMeritsDecisionDetails(meritsDecisionEntity))
        .build();
  }

  // MeritsDecisionEntity -> MeritsDecisionDetails
  private static MeritsDecisionDetails mapToMeritsDecisionDetails(MeritsDecisionEntity entity) {
    if (entity == null) {
      return null;
    }
    return MeritsDecisionDetails.builder()
        .decision(entity.getDecision())
        .justification(entity.getJustification())
        .reason(entity.getReason())
        .build();
  }
}
