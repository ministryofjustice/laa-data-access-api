package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.MakeDecisionCertificateGateway;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.MakeDecisionProceedingGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.CertificateDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.makedecision.MakeDecisionCommandGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.makedecision.MakeDecisionProceedingCommandGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class MakeDecisionUseCaseTest {

  @Mock private ApplicationGateway applicationGateway;
  @Mock private MakeDecisionCertificateGateway certificateGateway;
  @Mock private MakeDecisionProceedingGateway proceedingGateway;
  @Mock private DomainEventRepository domainEventRepository;
  @Mock private ServiceNameContext serviceNameContext;

  private MakeDecisionUseCase useCase;

  @BeforeEach
  void setUp() {
    SaveDomainEventService saveDomainEventService =
        new SaveDomainEventService(domainEventRepository, new ObjectMapper(), serviceNameContext);
    useCase =
        new MakeDecisionUseCase(
            applicationGateway, certificateGateway, proceedingGateway, saveDomainEventService);
    Mockito.lenient().when(serviceNameContext.getServiceName()).thenReturn(ServiceName.CIVIL_APPLY);
  }

  // ── helper ───────────────────────────────────────────────────────────────

  private ApplicationDomain applicationWithProceeding(UUID applicationId, UUID proceedingId) {
    ProceedingDomain proceeding = ProceedingDomain.builder().id(proceedingId).build();
    return DataGenerator.createDefault(
        ApplicationDomainGenerator.class,
        b ->
            b.id(applicationId)
                .version(0L)
                .caseworkerId(UUID.randomUUID())
                .proceedings(Set.of(proceeding)));
  }

  private MakeDecisionCommand grantedCommand(UUID applicationId, UUID proceedingId) {
    MakeDecisionProceedingCommand proc =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(proceedingId).justification("justification"));
    return DataGenerator.createDefault(
        MakeDecisionCommandGenerator.class,
        b ->
            b.applicationId(applicationId)
                .applicationVersion(0L)
                .overallDecision("GRANTED")
                .certificate(Map.of("k", "v"))
                .proceedings(List.of(proc)));
  }

  private MakeDecisionCommand refusedCommand(UUID applicationId, UUID proceedingId) {
    MakeDecisionProceedingCommand proc =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(proceedingId).decision("REFUSED").justification("justification"));
    return DataGenerator.createDefault(
        MakeDecisionCommandGenerator.class,
        b ->
            b.applicationId(applicationId)
                .applicationVersion(0L)
                .overallDecision("REFUSED")
                .certificate(null)
                .proceedings(List.of(proc)));
  }

  // ── happy paths ──────────────────────────────────────────────────────────

  @Test
  void
      givenGrantedDecisionWithNewCertificate_whenExecuted_thenApplicationSavedAndCertificateSaved() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionCommand command = grantedCommand(applicationId, proceedingId);

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));
    when(certificateGateway.findByApplicationId(applicationId)).thenReturn(Optional.empty());

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> appCaptor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).updateDecision(appCaptor.capture());
    assertThat(appCaptor.getValue().decision().overallDecision()).isEqualTo("GRANTED");
    ArgumentCaptor<CertificateDomain> certCaptor = ArgumentCaptor.forClass(CertificateDomain.class);
    verify(certificateGateway).save(certCaptor.capture());
    assertThat(certCaptor.getValue().applicationId()).isEqualTo(applicationId);
    assertThat(certCaptor.getValue().certificateContent()).isEqualTo(Map.of("k", "v"));
    ArgumentCaptor<DomainEventEntity> eventCaptor =
        ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(eventCaptor.getValue().getType())
        .isEqualTo(DomainEventType.APPLICATION_MAKE_DECISION_GRANTED);
  }

  @Test
  void givenGrantedDecisionWithExistingCertificate_whenExecuted_thenCertificateUpdated() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    UUID existingCertId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionCommand command = grantedCommand(applicationId, proceedingId);
    CertificateDomain existingCert =
        DataGenerator.createDefault(
            CertificateDomainGenerator.class,
            b -> b.id(existingCertId).applicationId(applicationId));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));
    when(certificateGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(existingCert));

    useCase.execute(command);

    ArgumentCaptor<CertificateDomain> certCaptor = ArgumentCaptor.forClass(CertificateDomain.class);
    verify(certificateGateway).save(certCaptor.capture());
    assertThat(certCaptor.getValue().id()).isEqualTo(existingCertId);
    assertThat(certCaptor.getValue().certificateContent()).isEqualTo(Map.of("k", "v"));
  }

  @Test
  void givenRefusedDecision_whenExecuted_thenApplicationSavedAndCertificateDeleted() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionCommand command = refusedCommand(applicationId, proceedingId);

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> appCaptor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).updateDecision(appCaptor.capture());
    assertThat(appCaptor.getValue().decision().overallDecision()).isEqualTo("REFUSED");
    assertThat(appCaptor.getValue().proceedings())
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.meritsDecision()).isNotNull();
              assertThat(p.meritsDecision().decision()).isEqualTo("REFUSED");
              assertThat(p.meritsDecision().justification()).isEqualTo("justification");
            });
    verify(certificateGateway).deleteByApplicationId(applicationId);
    verify(certificateGateway, never()).save(any());
  }

  // ── load failures ────────────────────────────────────────────────────────

  @Test
  void givenNoApplication_whenExecuted_thenThrowsResourceNotFoundException() {
    MakeDecisionCommand command = DataGenerator.createDefault(MakeDecisionCommandGenerator.class);
    when(applicationGateway.findByApplicationId(command.applicationId()))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining(command.applicationId().toString());

    verify(applicationGateway, never()).updateDecision(any());
  }

  @Test
  void givenMismatchedVersion_whenExecuted_thenThrowsOptimisticLockingException() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b -> b.applicationId(applicationId).applicationVersion(99L));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));

    assertThatExceptionOfType(OptimisticLockingFailureException.class)
        .isThrownBy(() -> useCase.execute(command));

    verify(applicationGateway, never()).updateDecision(any());
  }

  // ── validation failures ───────────────────────────────────────────────────

  @Test
  void givenEmptyProceedings_whenExecuted_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b -> b.applicationId(applicationId).applicationVersion(0L).proceedings(List.of()));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(
                        err ->
                            err.contains(
                                "The Make Decision request must contain at least one proceeding")));

    verify(applicationGateway, never()).updateDecision(any());
  }

  @Test
  void givenGrantedDecisionWithNullCertificate_whenExecuted_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionProceedingCommand proc =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(proceedingId).justification("j"));
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision("GRANTED")
                    .certificate(null)
                    .proceedings(List.of(proc)));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(
                        err ->
                            err.contains(
                                "The Make Decision request must contain a certificate when overallDecision is GRANTED")));
  }

  @Test
  void givenGrantedDecisionWithEmptyCertificate_whenExecuted_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionProceedingCommand proc =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(proceedingId).justification("j"));
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision("GRANTED")
                    .certificate(Map.of())
                    .proceedings(List.of(proc)));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(
            e ->
                assertThat(e.errors()).anyMatch(err -> err.contains("must contain a certificate")));
  }

  @Test
  void givenProceedingWithNullJustification_whenExecuted_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionProceedingCommand proc =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(proceedingId).justification(null));
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision("REFUSED")
                    .certificate(null)
                    .proceedings(List.of(proc)));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(
                        err ->
                            err.contains(
                                "must contain a refusal justification for proceeding with id: "
                                    + proceedingId)));
  }

  @Test
  void givenProceedingWithEmptyJustification_whenExecuted_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionProceedingCommand proc =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(proceedingId).justification(""));
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision("REFUSED")
                    .certificate(null)
                    .proceedings(List.of(proc)));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));

    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(err -> err.contains("must contain a refusal justification")));
  }

  @Test
  void givenProceedingNotFoundInDb_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    UUID linkedProceedingId = UUID.randomUUID();
    UUID unknownProceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, linkedProceedingId);

    MakeDecisionProceedingCommand proc =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(unknownProceedingId).justification("j"));
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision("REFUSED")
                    .certificate(null)
                    .proceedings(List.of(proc)));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));
    when(proceedingGateway.findExistingIds(List.of(unknownProceedingId))).thenReturn(Set.of());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining("No proceeding found with id: " + unknownProceedingId);
  }

  @Test
  void givenProceedingNotLinkedToApplication_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    UUID linkedProceedingId = UUID.randomUUID();
    UUID unlinkedProceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, linkedProceedingId);

    MakeDecisionProceedingCommand proc =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(unlinkedProceedingId).justification("j"));
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision("REFUSED")
                    .certificate(null)
                    .proceedings(List.of(proc)));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));
    when(proceedingGateway.findExistingIds(List.of(unlinkedProceedingId)))
        .thenReturn(Set.of(unlinkedProceedingId));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining("Not linked to application: " + unlinkedProceedingId);
  }

  @Test
  void
      givenProceedingsBothNotFoundAndNotLinked_whenExecuted_thenThrowsResourceNotFoundExceptionWithBothMessages() {
    UUID applicationId = UUID.randomUUID();
    UUID linkedProceedingId = UUID.randomUUID();
    UUID notFoundId = UUID.randomUUID();
    UUID notLinkedId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, linkedProceedingId);

    MakeDecisionProceedingCommand proc1 =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(notFoundId).justification("j"));
    MakeDecisionProceedingCommand proc2 =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(notLinkedId).justification("j"));
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision("REFUSED")
                    .certificate(null)
                    .proceedings(List.of(proc1, proc2)));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));
    when(proceedingGateway.findExistingIds(any())).thenReturn(Set.of(notLinkedId));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .satisfies(
            e -> {
              assertThat(e.getMessage()).contains("No proceeding found with id: " + notFoundId);
              assertThat(e.getMessage()).contains("Not linked to application: " + notLinkedId);
            });
  }

  // ── edge cases ──────────────────────────────────────────────────────────

  @Test
  void givenNullEventDescription_whenExecuted_thenDomainEventFiredSuccessfully() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision("REFUSED")
                    .certificate(null)
                    .eventDescription(null)
                    .proceedings(
                        List.of(
                            DataGenerator.createDefault(
                                MakeDecisionProceedingCommandGenerator.class,
                                p -> p.proceedingId(proceedingId).justification("j")))));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));

    useCase.execute(command);

    ArgumentCaptor<DomainEventEntity> eventCaptor =
        ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(eventCaptor.getValue().getType())
        .isEqualTo(DomainEventType.APPLICATION_MAKE_DECISION_REFUSED);
  }

  @Test
  void givenNullCaseworkerId_whenExecuted_thenDomainEventFiredWithNullCaseworkerId() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b ->
                b.id(applicationId)
                    .version(0L)
                    .caseworkerId(null)
                    .proceedings(Set.of(ProceedingDomain.builder().id(proceedingId).build())));
    MakeDecisionCommand command = refusedCommand(applicationId, proceedingId);

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));

    useCase.execute(command);

    ArgumentCaptor<DomainEventEntity> eventCaptor =
        ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(eventCaptor.getValue().getCaseworkerId()).isNull();
  }

  @Test
  void
      givenMultipleProceedings_whenExecuted_thenOnlyCommandedProceedingsHaveMeritsDecisionApplied() {
    UUID applicationId = UUID.randomUUID();
    UUID updatedId = UUID.randomUUID();
    UUID untouchedId = UUID.randomUUID();

    ProceedingDomain updatedProceeding = ProceedingDomain.builder().id(updatedId).build();
    ProceedingDomain untouchedProceeding =
        ProceedingDomain.builder().id(untouchedId).description("original").isLead(true).build();

    ApplicationDomain application =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b ->
                b.id(applicationId)
                    .version(0L)
                    .caseworkerId(UUID.randomUUID())
                    .proceedings(Set.of(updatedProceeding, untouchedProceeding)));

    MakeDecisionProceedingCommand procCmd =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(updatedId).decision("REFUSED").justification("j").reason("r"));
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision("REFUSED")
                    .certificate(null)
                    .proceedings(List.of(procCmd)));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> appCaptor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).updateDecision(appCaptor.capture());

    assertThat(appCaptor.getValue().proceedings()).hasSize(2);
    assertThat(appCaptor.getValue().proceedings())
        .filteredOn(p -> updatedId.equals(p.id()))
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.meritsDecision()).isNotNull();
              assertThat(p.meritsDecision().decision()).isEqualTo("REFUSED");
              assertThat(p.meritsDecision().justification()).isEqualTo("j");
              assertThat(p.meritsDecision().reason()).isEqualTo("r");
            });

    assertThat(appCaptor.getValue().proceedings())
        .filteredOn(p -> untouchedId.equals(p.id()))
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.description()).isEqualTo("original");
              assertThat(p.isLead()).isTrue();
              assertThat(p.meritsDecision()).isNull();
            });
  }

  @Test
  void givenCommandWithAutoGrantedTrue_whenExecuted_thenUpdatedApplicationHasAutoGrantedTrue() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationDomain application = applicationWithProceeding(applicationId, proceedingId);
    MakeDecisionProceedingCommand proc =
        DataGenerator.createDefault(
            MakeDecisionProceedingCommandGenerator.class,
            b -> b.proceedingId(proceedingId).justification("j"));
    MakeDecisionCommand command =
        DataGenerator.createDefault(
            MakeDecisionCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision("GRANTED")
                    .autoGranted(true)
                    .certificate(Map.of("k", "v"))
                    .proceedings(List.of(proc)));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(application));
    when(certificateGateway.findByApplicationId(applicationId)).thenReturn(Optional.empty());

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> appCaptor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).updateDecision(appCaptor.capture());
    assertThat(appCaptor.getValue().isAutoGranted()).isTrue();
  }
}
