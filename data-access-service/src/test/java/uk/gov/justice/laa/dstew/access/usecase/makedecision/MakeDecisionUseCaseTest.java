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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
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
import uk.gov.justice.laa.dstew.access.utils.generator.decision.MeritsDecisionDomainGenerator;
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
  void givenValidRefusedCommand_whenMakeDecision_thenDomainFieldsMappedFromCommand() {
    MakeDecisionCommand command = defaultCommand();
    when(applicationGateway.loadById(applicationId)).thenReturn(application);
    when(applicationGateway.save(any())).thenReturn(application);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(false);

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(captor.capture());
    ApplicationDomain domain = captor.getValue();

    // isAutoGranted and overallDecision are transferred directly from the command
    assertThat(domain.isAutoGranted()).isEqualTo(command.autoGranted());
    assertThat(domain.decision()).isNotNull();
    assertThat(domain.decision().overallDecision()).isEqualTo(command.overallDecision());

    // Each proceeding's meritsDecision fields are patched from the corresponding command proceeding
    MakeDecisionProceedingCommand procCmd = command.proceedings().getFirst();
    ProceedingDomain updatedProceeding =
        domain.proceedings().stream()
            .filter(p -> p.id().equals(proceedingId))
            .findFirst()
            .orElseThrow();
    assertThat(updatedProceeding.meritsDecision()).isNotNull();
    assertThat(updatedProceeding.meritsDecision().decision()).isEqualTo(procCmd.meritsDecision());
    assertThat(updatedProceeding.meritsDecision().reason()).isEqualTo(procCmd.reason());
    assertThat(updatedProceeding.meritsDecision().justification()).isEqualTo(procCmd.justification());

    verify(domainEventGateway)
        .saveDecisionEvent(
            eq(applicationId), any(), any(), any(), eq(OverallDecisionStatus.REFUSED));
    verify(certificateGateway, never()).deleteByApplicationId(any());
  }

  @Test
  void givenRefusedDecisionWithExistingCertificate_whenMakeDecision_thenDeletesCertificate() {
    MakeDecisionCommand command = defaultCommand();
    when(applicationGateway.loadById(applicationId)).thenReturn(application);
    when(applicationGateway.save(any())).thenReturn(application);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(true);

    useCase.execute(command);

    verify(certificateGateway).deleteByApplicationId(applicationId);
  }

  @Test
  void givenValidGrantedCommand_whenMakeDecision_thenDomainFieldsMappedFromCommand() {
    Map<String, Object> cert = Map.of("key", "value");
    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .autoGranted(true)
                    .overallDecision(OverallDecisionStatus.GRANTED)
                    .certificate(cert)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(proceedingId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .reason("granted reason")
                                .justification("granted justification")
                                .build())));
    when(applicationGateway.loadById(applicationId)).thenReturn(application);
    when(applicationGateway.save(any())).thenReturn(application);

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(captor.capture());
    ApplicationDomain domain = captor.getValue();

    // isAutoGranted and overallDecision are transferred directly from the command
    assertThat(domain.isAutoGranted()).isTrue();
    assertThat(domain.decision()).isNotNull();
    assertThat(domain.decision().overallDecision()).isEqualTo(OverallDecisionStatus.GRANTED);

    // Each proceeding's meritsDecision fields are patched from the corresponding command proceeding
    MakeDecisionProceedingCommand procCmd = command.proceedings().getFirst();
    ProceedingDomain updatedProceeding =
        domain.proceedings().stream()
            .filter(p -> p.id().equals(proceedingId))
            .findFirst()
            .orElseThrow();
    assertThat(updatedProceeding.meritsDecision()).isNotNull();
    assertThat(updatedProceeding.meritsDecision().decision()).isEqualTo(MeritsDecisionOutcome.GRANTED);
    assertThat(updatedProceeding.meritsDecision().reason()).isEqualTo(procCmd.reason());
    assertThat(updatedProceeding.meritsDecision().justification()).isEqualTo(procCmd.justification());

    verify(certificateGateway).saveOrUpdate(eq(applicationId), eq(cert));
    verify(domainEventGateway)
        .saveDecisionEvent(
            eq(applicationId), any(), any(), any(), eq(OverallDecisionStatus.GRANTED));
    verify(certificateGateway, never()).deleteByApplicationId(any());
  }

  @Test
  void givenApplicationWithExistingDecision_whenMakeDecision_thenOverallDecisionUpdated() {
    ApplicationDomain appWithDecision =
        application.toBuilder().decision(decisionGenerator.createDefault()).build();
    MakeDecisionCommand command = defaultCommand();
    when(applicationGateway.loadById(applicationId)).thenReturn(appWithDecision);
    when(applicationGateway.save(any())).thenReturn(appWithDecision);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(false);

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(captor.capture());
    ApplicationDomain domain = captor.getValue();

    // The existing decision is updated with the command's overallDecision — not replaced wholesale
    assertThat(domain.decision()).isNotNull();
    assertThat(domain.decision().overallDecision()).isEqualTo(command.overallDecision());
  }

  @Test
  void givenApplicationNotFound_whenMakeDecision_thenThrowResourceNotFoundException() {
    MakeDecisionCommand command = defaultCommand();
    when(applicationGateway.loadById(applicationId))
        .thenThrow(new ResourceNotFoundException("No application found"));

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenUnknownProceedingId_whenMakeDecision_thenThrowResourceNotFoundException() {
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
  void givenEmptyProceedings_whenMakeDecision_thenThrowValidationException() {
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
  void givenGrantedDecisionWithoutCertificate_whenMakeDecision_thenThrowValidationException() {
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
  void givenGrantedDecisionWithEmptyCertificate_whenMakeDecision_thenThrowValidationException() {
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
  void givenProceedingWithEmptyJustification_whenMakeDecision_thenThrowValidationException() {
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
  void givenProceedingWithNullJustification_whenMakeDecision_thenThrowValidationException() {
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
  void givenStaleApplicationVersion_whenMakeDecision_thenThrowOptimisticLockingFailureException() {
    MakeDecisionCommand command =
        commandGenerator.createDefault(b -> b.applicationId(applicationId).applicationVersion(99L));
    when(applicationGateway.loadById(applicationId)).thenReturn(application); // version=0

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(OptimisticLockingFailureException.class);

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenMultipleProceedings_whenMakeDecision_thenAllProceedingsPatchedFromCommand() {
    UUID grantedProceedingId = UUID.randomUUID();
    UUID refusedProceedingId = UUID.randomUUID();

    ProceedingDomain grantedProceeding =
        proceedingGenerator.createDefault(b -> b.id(grantedProceedingId).meritsDecision(null));
    ProceedingDomain refusedProceeding =
        proceedingGenerator.createDefault(b -> b.id(refusedProceedingId).meritsDecision(null));

    ApplicationDomain appWithTwoProceedings =
        applicationDomainGenerator
            .createWithSpecificId(applicationId)
            .toBuilder()
            .proceedings(List.of(grantedProceeding, refusedProceeding))
            .decision(null)
            .build();

    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .overallDecision(OverallDecisionStatus.REFUSED)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(grantedProceedingId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .reason("granted reason")
                                .justification("granted justification")
                                .build(),
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(refusedProceedingId)
                                .meritsDecision(MeritsDecisionOutcome.REFUSED)
                                .reason("refused reason")
                                .justification("refused justification")
                                .build())));

    when(applicationGateway.loadById(applicationId)).thenReturn(appWithTwoProceedings);
    when(applicationGateway.save(any())).thenReturn(appWithTwoProceedings);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(false);

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(captor.capture());
    ApplicationDomain saved = captor.getValue();

    ProceedingDomain savedGranted =
        saved.proceedings().stream()
            .filter(p -> p.id().equals(grantedProceedingId))
            .findFirst()
            .orElseThrow();
    assertThat(savedGranted.meritsDecision()).isNotNull();
    assertThat(savedGranted.meritsDecision().decision()).isEqualTo(MeritsDecisionOutcome.GRANTED);
    assertThat(savedGranted.meritsDecision().reason()).isEqualTo("granted reason");
    assertThat(savedGranted.meritsDecision().justification()).isEqualTo("granted justification");

    ProceedingDomain savedRefused =
        saved.proceedings().stream()
            .filter(p -> p.id().equals(refusedProceedingId))
            .findFirst()
            .orElseThrow();
    assertThat(savedRefused.meritsDecision()).isNotNull();
    assertThat(savedRefused.meritsDecision().decision()).isEqualTo(MeritsDecisionOutcome.REFUSED);
    assertThat(savedRefused.meritsDecision().reason()).isEqualTo("refused reason");
    assertThat(savedRefused.meritsDecision().justification()).isEqualTo("refused justification");
  }

  @Test
  void givenProceedingNotInCommand_whenMakeDecision_thenUnmentionedProceedingIsLeftUntouched() {
    UUID commandProceedingId = UUID.randomUUID();
    UUID untouchedProceedingId = UUID.randomUUID();

    MeritsDecisionDomain existingMerits =
        new MeritsDecisionDomainGenerator()
            .createDefault(
                b -> b.decision(MeritsDecisionOutcome.REFUSED).reason("old").justification("old"));

    ProceedingDomain commandProceeding =
        proceedingGenerator.createDefault(b -> b.id(commandProceedingId).meritsDecision(null));
    ProceedingDomain untouchedProceeding =
        proceedingGenerator.createDefault(
            b -> b.id(untouchedProceedingId).meritsDecision(existingMerits));

    ApplicationDomain appWithTwoProceedings =
        applicationDomainGenerator
            .createWithSpecificId(applicationId)
            .toBuilder()
            .proceedings(List.of(commandProceeding, untouchedProceeding))
            .decision(null)
            .build();

    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(commandProceedingId)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .reason("new reason")
                                .justification("new justification")
                                .build())));

    when(applicationGateway.loadById(applicationId)).thenReturn(appWithTwoProceedings);
    when(applicationGateway.save(any())).thenReturn(appWithTwoProceedings);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(false);

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(captor.capture());
    ApplicationDomain saved = captor.getValue();

    ProceedingDomain savedUntouched =
        saved.proceedings().stream()
            .filter(p -> p.id().equals(untouchedProceedingId))
            .findFirst()
            .orElseThrow();
    assertThat(savedUntouched.meritsDecision()).isEqualTo(existingMerits);
  }

  @Test
  void givenRefusedDecision_whenMakeDecision_thenCertificateSaveOrUpdateNeverCalled() {
    MakeDecisionCommand command = defaultCommand();
    when(applicationGateway.loadById(applicationId)).thenReturn(application);
    when(applicationGateway.save(any())).thenReturn(application);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(false);

    useCase.execute(command);

    verify(certificateGateway, never()).saveOrUpdate(any(), any());
  }

  @Test
  void givenExistingMeritsDecisionOnProceeding_whenMakeDecision_thenMeritsDecisionFieldsUpdated() {
    MeritsDecisionDomain existingMerits =
        new MeritsDecisionDomainGenerator()
            .createDefault(
                b ->
                    b.decision(MeritsDecisionOutcome.REFUSED)
                        .reason("old reason")
                        .justification("old justification"));

    ProceedingDomain proceedingWithExistingMerits =
        proceedingGenerator.createDefault(
            b -> b.id(proceedingId).meritsDecision(existingMerits));

    ApplicationDomain appWithExistingMerits =
        applicationDomainGenerator
            .createWithSpecificId(applicationId)
            .toBuilder()
            .proceedings(List.of(proceedingWithExistingMerits))
            .decision(decisionGenerator.createDefault())
            .build();

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
                                .reason("updated reason")
                                .justification("updated justification")
                                .build())));

    when(applicationGateway.loadById(applicationId)).thenReturn(appWithExistingMerits);
    when(applicationGateway.save(any())).thenReturn(appWithExistingMerits);
    when(certificateGateway.existsByApplicationId(applicationId)).thenReturn(false);

    useCase.execute(command);

    ArgumentCaptor<ApplicationDomain> captor = ArgumentCaptor.forClass(ApplicationDomain.class);
    verify(applicationGateway).save(captor.capture());
    ProceedingDomain updated =
        captor.getValue().proceedings().stream()
            .filter(p -> p.id().equals(proceedingId))
            .findFirst()
            .orElseThrow();

    assertThat(updated.meritsDecision().decision()).isEqualTo(MeritsDecisionOutcome.GRANTED);
    assertThat(updated.meritsDecision().reason()).isEqualTo("updated reason");
    assertThat(updated.meritsDecision().justification()).isEqualTo("updated justification");
  }

  @Test
  void givenMultipleMissingProceedingIds_whenMakeDecision_thenExceptionMessageContainsAllIds() {
    UUID unknownId1 = UUID.randomUUID();
    UUID unknownId2 = UUID.randomUUID();

    MakeDecisionCommand command =
        commandGenerator.createDefault(
            b ->
                b.applicationId(applicationId)
                    .applicationVersion(0L)
                    .proceedings(
                        List.of(
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(unknownId1)
                                .meritsDecision(MeritsDecisionOutcome.GRANTED)
                                .justification("justification1")
                                .build(),
                            MakeDecisionProceedingCommand.builder()
                                .proceedingId(unknownId2)
                                .meritsDecision(MeritsDecisionOutcome.REFUSED)
                                .justification("justification2")
                                .build())));

    // Application has a different proceeding — neither unknown id will be found
    when(applicationGateway.loadById(applicationId)).thenReturn(application);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(unknownId1.toString())
        .hasMessageContaining(unknownId2.toString());

    verify(applicationGateway, never()).save(any());
  }

  @Test
  void givenExecuteMethod_whenInspected_thenCarriesEnforceRoleAnnotationForCaseworker() throws NoSuchMethodException {
    Method method = MakeDecisionUseCase.class.getMethod("execute", MakeDecisionCommand.class);
    EnforceRole annotation = method.getAnnotation(EnforceRole.class);
    assert annotation != null;
    assert annotation.anyOf().length == 1;
    assert annotation.anyOf()[0] == RequiredRole.API_CASEWORKER;
  }
}
