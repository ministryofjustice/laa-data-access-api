package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionOutcome;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.CertificateGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.EnforceRole;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.RequiredRole;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.MakeDecisionCommandGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingDomainGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class MakeDecisionUseCaseTest {

  @Mock private ApplicationGateway applicationGateway;
  @Mock private CertificateGateway certificateGateway;
  @Mock private DomainEventGateway domainEventGateway;

  private MakeDecisionUseCase useCase;
  private final MakeDecisionCommandGenerator commandGenerator = new MakeDecisionCommandGenerator();
  private final DecisionDomainGenerator decisionGenerator = new DecisionDomainGenerator();
  private final ApplicationDomainGenerator applicationDomainGenerator =
      new ApplicationDomainGenerator();
  private final ProceedingDomainGenerator proceedingGenerator = new ProceedingDomainGenerator();

  private UUID applicationId;
  private UUID proceedingId;
  private ApplicationDomain application;

  @BeforeEach
  void setUp() {
    useCase = new MakeDecisionUseCase(applicationGateway, certificateGateway, domainEventGateway);
    applicationId = UUID.randomUUID();
    proceedingId = UUID.randomUUID();
    // Application has one proceeding whose id matches the command proceeding id
    ProceedingDomain proceeding = proceedingGenerator.createDefault(b -> b.id(proceedingId));
    application =
        applicationDomainGenerator.createWithSpecificId(applicationId).toBuilder()
            .proceedings(List.of(proceeding))
            .decision(null)
            .build();
  }

  private MakeDecisionCommand defaultCommand() {
    return commandGenerator.createDefault(
        b ->
            b.applicationId(applicationId)
                .applicationVersion(0L)
                .proceedings(
                    List.of(
                        MakeDecisionProceedingCommand.builder()
                            .proceedingId(proceedingId)
                            .meritsDecision(MeritsDecisionOutcome.REFUSED)
                            .justification("justification")
                            .build())));
  }

  @Test
  void givenValidRefusedCommand_whenExecute_thenSucceeds() {
    MakeDecisionCommand command = defaultCommand();
    when(applicationGateway.loadById(applicationId)).thenReturn(application);
    when(applicationGateway.save(any())).thenReturn(application);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(false);

    useCase.execute(command);

    verify(applicationGateway).save(argThat(d -> d.decision() != null));
    verify(domainEventGateway)
        .saveDecisionEvent(
            eq(applicationId), any(), any(), any(), eq(OverallDecisionStatus.REFUSED));
    verify(certificateGateway, never()).deleteByApplicationId(any());
  }

  @Test
  void givenRefusedWithExistingCertificate_whenExecute_thenDeletesCertificate() {
    MakeDecisionCommand command = defaultCommand();
    when(applicationGateway.loadById(applicationId)).thenReturn(application);
    when(applicationGateway.save(any())).thenReturn(application);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(true);

    useCase.execute(command);

    verify(certificateGateway).deleteByApplicationId(applicationId);
  }

  @Test
  void givenGrantedWithCertificate_whenExecute_thenSavesCertificate() {
    Map<String, Object> cert = Map.of("key", "value");
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
                                .proceedingId(proceedingId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .justification("justification")
                                .build())));
    when(applicationGateway.loadById(applicationId)).thenReturn(application);
    when(applicationGateway.save(any())).thenReturn(application);

    useCase.execute(command);

    verify(certificateGateway).saveOrUpdate(eq(applicationId), eq(cert));
    verify(domainEventGateway)
        .saveDecisionEvent(
            eq(applicationId), any(), any(), any(), eq(OverallDecisionStatus.GRANTED));
    verify(certificateGateway, never()).deleteByApplicationId(any());
  }

  @Test
  void givenExistingDecision_whenExecute_thenUpdatesDecision() {
    ApplicationDomain appWithDecision =
        application.toBuilder().decision(decisionGenerator.createDefault()).build();
    MakeDecisionCommand command = defaultCommand();
    when(applicationGateway.loadById(applicationId)).thenReturn(appWithDecision);
    when(applicationGateway.save(any())).thenReturn(appWithDecision);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(false);

    useCase.execute(command);

    verify(applicationGateway).save(argThat(d -> d.decision() != null));
  }

  @Test
  void givenApplicationNotFound_whenExecute_thenThrowsResourceNotFoundException() {
    MakeDecisionCommand command = defaultCommand();
    when(applicationGateway.loadById(applicationId))
        .thenThrow(new ResourceNotFoundException("No application found"));

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenProceedingNotFound_whenExecute_thenThrowsAndNeverSaves() {
    UUID unknownProceedingId = UUID.randomUUID();
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(unknownProceedingId)
                                .meritsDecision(MeritsDecisionOutcome.REFUSED)
                                .justification("justification")
                                .build())));
    // Application has a different proceeding id — unknown one won't be found
    when(applicationGateway.loadById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenEmptyProceedings_whenExecute_thenThrowsValidationException() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b -> b.applicationId(applicationId).applicationVersion(0L).proceedings(List.of()));
    when(applicationGateway.loadById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            e ->
                assertThat(((ValidationException) e).errors())
                    .anyMatch(msg -> msg.contains("at least one proceeding")));

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenGrantedWithoutCertificate_whenExecute_thenThrowsValidationException() {
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
                                .proceedingId(proceedingId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .justification("justification")
                                .build())));
    when(applicationGateway.loadById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            e ->
                assertThat(((ValidationException) e).errors())
                    .anyMatch(msg -> msg.contains("certificate")));

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenGrantedWithEmptyCertificate_whenExecute_thenThrowsValidationException() {
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
                                .proceedingId(proceedingId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .justification("justification")
                                .build())));
    when(applicationGateway.loadById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(ValidationException.class);
  }

  @Test
  void givenProceedingWithEmptyJustification_whenExecute_thenThrowsValidationException() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(proceedingId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .justification("")
                                .build())));
    when(applicationGateway.loadById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            e ->
                assertThat(((ValidationException) e).errors())
                    .anyMatch(
                        msg ->
                            msg.contains(
                                "refusal justification for proceeding with id: " + proceedingId)));

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenProceedingWithNullJustification_whenExecute_thenThrowsValidationException() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(proceedingId)
                                .meritsDecision(MeritsDecisionOutcome.REFUSED)
                                .justification(null)
                                .build())));
    when(applicationGateway.loadById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            e ->
                assertThat(((ValidationException) e).errors())
                    .anyMatch(msg -> msg.contains("refusal justification")));

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenVersionMismatch_whenExecute_thenThrowsOptimisticLockingFailureException() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(b -> b.applicationId(applicationId).applicationVersion(99L));
    when(applicationGateway.loadById(applicationId)).thenReturn(application); // version=0

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(OptimisticLockingFailureException.class);

    verify(applicationGateway, never()).save(any());
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
