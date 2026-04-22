package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionOutcome;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.CertificateGateway;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.DecisionGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ProceedingGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.EnforceRole;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.RequiredRole;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.MakeDecisionCommandGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class MakeDecisionUseCaseTest {

  @Mock private ApplicationGateway applicationGateway;
  @Mock private ProceedingGateway proceedingGateway;
  @Mock private DecisionGateway decisionGateway;
  @Mock private CertificateGateway certificateGateway;
  @Mock private DomainEventGateway domainEventGateway;

  private MakeDecisionUseCase useCase;
  private final MakeDecisionCommandGenerator commandGenerator = new MakeDecisionCommandGenerator();
  private final DecisionDomainGenerator decisionGenerator = new DecisionDomainGenerator();
  private final ApplicationDomainGenerator applicationDomainGenerator =
      new ApplicationDomainGenerator();

  private UUID applicationId;
  private ApplicationDomain application;

  @BeforeEach
  void setUp() {
    useCase =
        new MakeDecisionUseCase(
            applicationGateway,
            proceedingGateway,
            decisionGateway,
            certificateGateway,
            domainEventGateway);
    applicationId = UUID.randomUUID();
    application = applicationDomainGenerator.createWithSpecificId(applicationId);
  }

  @Test
  void givenValidRefusedCommand_whenExecute_thenSucceeds() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(b -> b.applicationId(applicationId).applicationVersion(0L));
    when(applicationGateway.findById(applicationId)).thenReturn(application);
    when(decisionGateway.findByApplicationId(applicationId)).thenReturn(null);
    when(decisionGateway.saveAndLink(eq(applicationId), any()))
        .thenReturn(decisionGenerator.createDefault());
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(false);

    useCase.execute(command);

    verify(applicationGateway).updateAutoGranted(eq(applicationId), any());
    verify(decisionGateway).saveAndLink(eq(applicationId), any());
    verify(domainEventGateway)
        .saveDecisionEvent(
            eq(applicationId), any(), any(), any(), eq(OverallDecisionStatus.REFUSED));
    verify(certificateGateway, never()).deleteByApplicationId(any());
  }

  @Test
  void givenRefusedWithExistingCertificate_whenExecute_thenDeletesCertificate() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(b -> b.applicationId(applicationId).applicationVersion(0L));
    when(applicationGateway.findById(applicationId)).thenReturn(application);
    when(decisionGateway.findByApplicationId(applicationId)).thenReturn(null);
    when(decisionGateway.saveAndLink(eq(applicationId), any()))
        .thenReturn(decisionGenerator.createDefault());
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(true);

    useCase.execute(command);

    verify(certificateGateway).deleteByApplicationId(applicationId);
  }

  @Test
  void givenGrantedWithCertificate_whenExecute_thenSavesCertificate() {
    Map<String, Object> cert = Map.of("key", "value");
    UUID procId = UUID.randomUUID();
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision(OverallDecisionStatus.GRANTED)
                    .certificate(cert)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(procId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .justification("justification")
                                .build())));
    when(applicationGateway.findById(applicationId)).thenReturn(application);
    when(decisionGateway.findByApplicationId(applicationId)).thenReturn(null);
    when(decisionGateway.saveAndLink(eq(applicationId), any()))
        .thenReturn(decisionGenerator.createDefault());

    useCase.execute(command);

    verify(certificateGateway).saveOrUpdate(eq(applicationId), eq(cert));
    verify(domainEventGateway)
        .saveDecisionEvent(
            eq(applicationId), any(), any(), any(), eq(OverallDecisionStatus.GRANTED));
    verify(certificateGateway, never()).deleteByApplicationId(any());
  }

  @Test
  void givenExistingDecision_whenExecute_thenUpdatesDecision() {
    DecisionDomain existing = decisionGenerator.createDefault();
    MakeDecisionCommand command =
        commandGenerator.createDefault(b -> b.applicationId(applicationId).applicationVersion(0L));
    when(applicationGateway.findById(applicationId)).thenReturn(application);
    when(decisionGateway.findByApplicationId(applicationId)).thenReturn(existing);
    when(decisionGateway.saveAndLink(eq(applicationId), any())).thenReturn(existing);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(false);

    useCase.execute(command);

    verify(decisionGateway).saveAndLink(eq(applicationId), any());
  }

  @Test
  void givenApplicationNotFound_whenExecute_thenThrowsResourceNotFoundException() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(b -> b.applicationId(applicationId));
    when(applicationGateway.findById(applicationId))
        .thenThrow(new ResourceNotFoundException("No application found"));

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(applicationGateway, never()).updateAutoGranted(any(), any());
    verify(decisionGateway, never()).saveAndLink(any(), any());
  }

  @Test
  void givenProceedingNotFound_whenExecute_thenThrowsAndNeverSaves() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(b -> b.applicationId(applicationId).applicationVersion(0L));
    when(applicationGateway.findById(applicationId)).thenReturn(application);
    when(proceedingGateway.findAllByIds(any(), any()))
        .thenThrow(new ResourceNotFoundException("No proceeding found with id: xxx"));

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(applicationGateway, never()).updateAutoGranted(any(), any());
    verify(decisionGateway, never()).saveAndLink(any(), any());
  }

  @Test
  void givenEmptyProceedings_whenExecute_thenThrowsValidationException() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b -> b.applicationId(applicationId).applicationVersion(0L).proceedings(List.of()));
    when(applicationGateway.findById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            e ->
                assertThat(((ValidationException) e).errors())
                    .anyMatch(msg -> msg.contains("at least one proceeding")));

    verify(applicationGateway, never()).updateAutoGranted(any(), any());
  }

  @Test
  void givenGrantedWithoutCertificate_whenExecute_thenThrowsValidationException() {
    UUID procId = UUID.randomUUID();
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision(OverallDecisionStatus.GRANTED)
                    .certificate(null)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(procId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .justification("justification")
                                .build())));
    when(applicationGateway.findById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            e ->
                assertThat(((ValidationException) e).errors())
                    .anyMatch(msg -> msg.contains("certificate")));

    verify(applicationGateway, never()).updateAutoGranted(any(), any());
  }

  @Test
  void givenGrantedWithEmptyCertificate_whenExecute_thenThrowsValidationException() {
    UUID procId = UUID.randomUUID();
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision(OverallDecisionStatus.GRANTED)
                    .certificate(Map.of())
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(procId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .justification("justification")
                                .build())));
    when(applicationGateway.findById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            e ->
                assertThat(((ValidationException) e).errors())
                    .anyMatch(msg -> msg.contains("certificate")));
  }

  @Test
  void givenProceedingWithEmptyJustification_whenExecute_thenThrowsValidationException() {
    UUID procId = UUID.randomUUID();
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(procId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .justification("")
                                .build())));
    when(applicationGateway.findById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            e ->
                assertThat(((ValidationException) e).errors())
                    .anyMatch(
                        msg ->
                            msg.contains(
                                "refusal justification for proceeding with id: " + procId)));

    verify(applicationGateway, never()).updateAutoGranted(any(), any());
  }

  @Test
  void givenProceedingWithNullJustification_whenExecute_thenThrowsValidationException() {
    UUID procId = UUID.randomUUID();
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(procId)
                                .meritsDecision(MeritsDecisionOutcome.REFUSED)
                                .justification(null)
                                .build())));
    when(applicationGateway.findById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            e ->
                assertThat(((ValidationException) e).errors())
                    .anyMatch(msg -> msg.contains("refusal justification")));

    verify(applicationGateway, never()).updateAutoGranted(any(), any());
  }

  @Test
  void givenVersionMismatch_whenExecute_thenThrowsOptimisticLockingFailureException() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(b -> b.applicationId(applicationId).applicationVersion(99L));
    when(applicationGateway.findById(applicationId)).thenReturn(application); // version=0

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(OptimisticLockingFailureException.class);

    verify(applicationGateway, never()).updateAutoGranted(any(), any());
  }

  @Test
  void givenExecuteMethod_thenCarriesEnforceRoleAnnotation() throws NoSuchMethodException {
    Method method = MakeDecisionUseCase.class.getMethod("execute", MakeDecisionCommand.class);
    EnforceRole annotation = method.getAnnotation(EnforceRole.class);
    assert annotation != null;
    assert annotation.anyOf().length == 1;
    assert annotation.anyOf()[0] == RequiredRole.API_CASEWORKER;
  }
}
