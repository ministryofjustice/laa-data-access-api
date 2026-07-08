package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.domain.enums.DecisionStatus;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.MakeDecisionApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.MakeDecisionCertificateGateway;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.MakeDecisionProceedingGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.utils.VersionCheckHelper;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Orchestrates the full make-decision flow. */
@RequiredArgsConstructor
public class MakeDecisionUseCase {

  private final ApplicationGateway applicationGateway;
  private final MakeDecisionApplicationGateway makeDecisionApplicationGateway;
  private final MakeDecisionCertificateGateway certificateGateway;
  private final MakeDecisionProceedingGateway proceedingGateway;
  private final SaveDomainEventService saveDomainEventService;

  /**
   * Executes the makeDecision use case.
   *
   * @param command the command carrying all fields from the HTTP request
   */
  @AllowApiCaseworker
  @Transactional
  public void execute(MakeDecisionCommand command) {
    ApplicationDomain application =
        applicationGateway
            .findByApplicationId(command.applicationId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        String.format(
                            "No application found with id: %s", command.applicationId())));

    VersionCheckHelper.checkEntityVersionLocking(
        command.applicationId(), application.version(), command.applicationVersion());

    // Inline validation (before any write)
    validateCommand(command);

    // Validate proceedings are linked
    validateProceedings(application, command);

    // Merge merits decisions from command into the existing proceedings
    Map<UUID, MakeDecisionProceedingCommand> commandByProceedingId =
        command.proceedings().stream()
            .collect(Collectors.toMap(MakeDecisionProceedingCommand::proceedingId, p -> p));

    Set<ProceedingDomain> mergedProceedings =
        application.proceedings().stream()
            .map(existing -> handleProceedingDomain(existing, commandByProceedingId))
            .collect(Collectors.toCollection(LinkedHashSet::new));

    // Build updated application domain
    ApplicationDomain updatedApplication =
        application.toBuilder()
            .decision(DecisionDomain.builder().overallDecision(command.overallDecision()).build())
            .isAutoGranted(command.autoGranted())
            .proceedings(mergedProceedings)
            .build();

    // Persist application changes via the makeDecision-specific gateway
    makeDecisionApplicationGateway.updateDecision(updatedApplication);

    // Certificate handling
    if (DecisionStatus.GRANTED == command.overallDecision()) {
      handleGrantedDecision(command);
    } else {
      certificateGateway.deleteByApplicationId(command.applicationId());
    }

    // Fire domain event
    saveDomainEventService.saveMakeDecisionDomainEvent(
        command.applicationId(),
        command.serialisedRequest(),
        application.caseworkerId(),
        command.overallDecision(),
        command.eventDescription());
  }

  private ProceedingDomain handleProceedingDomain(
      ProceedingDomain existing, Map<UUID, MakeDecisionProceedingCommand> commandByProceedingId) {
    MakeDecisionProceedingCommand procCmd = commandByProceedingId.get(existing.id());

    if (procCmd == null) {
      return existing;
    }

    return existing.toBuilder()
        .meritsDecision(
            MeritsDecisionDomain.builder()
                .decision(procCmd.decision())
                .reason(procCmd.reason())
                .justification(procCmd.justification())
                .modifiedAt(Instant.now())
                .build())
        .build();
  }

  private void handleGrantedDecision(MakeDecisionCommand command) {
    CertificateDomain certificate =
        certificateGateway
            .findByApplicationId(command.applicationId())
            .map(existing -> existing.toBuilder().certificateContent(command.certificate()).build())
            .orElseGet(
                () ->
                    CertificateDomain.builder()
                        .applicationId(command.applicationId())
                        .certificateContent(command.certificate())
                        .build());
    certificateGateway.save(certificate);
  }

  private void validateCommand(MakeDecisionCommand command) {
    if (command.proceedings().isEmpty()) {
      throw new ValidationException(
          List.of("The Make Decision request must contain at least one proceeding"));
    }
    if (DecisionStatus.GRANTED == command.overallDecision()
        && isCertificateNullOrEmpty(command.certificate())) {
      throw new ValidationException(
          List.of(
              "The Make Decision request must contain a certificate when overallDecision is GRANTED"));
    }
    command.proceedings().forEach(this::validateProceedingCommand);
  }

  private void validateProceedingCommand(MakeDecisionProceedingCommand proceedingCommand) {
    if (proceedingCommand.justification() == null || proceedingCommand.justification().isEmpty()) {
      throw new ValidationException(
          List.of(
              "The Make Decision request must contain a refusal justification for proceeding with id: "
                  + proceedingCommand.proceedingId()));
    }
  }

  private void validateProceedings(ApplicationDomain application, MakeDecisionCommand command) {
    Map<UUID, ProceedingDomain> linkedMap =
        application.proceedings().stream().collect(Collectors.toMap(ProceedingDomain::id, p -> p));

    List<UUID> notLinkedIds =
        command.proceedings().stream()
            .map(MakeDecisionProceedingCommand::proceedingId)
            .distinct()
            .filter(id -> !linkedMap.containsKey(id))
            .toList();

    if (notLinkedIds.isEmpty()) {
      return;
    }

    Set<UUID> foundInDbIds = proceedingGateway.findExistingIds(notLinkedIds);

    List<String> errors = new ArrayList<>();
    List<UUID> notFoundIdList =
        notLinkedIds.stream().filter(id -> !foundInDbIds.contains(id)).toList();
    if (!notFoundIdList.isEmpty()) {
      errors.add(
          "No proceeding found with id: "
              + notFoundIdList.stream().map(UUID::toString).collect(Collectors.joining(",")));
    }
    if (!foundInDbIds.isEmpty()) {
      errors.add(
          "Not linked to application: "
              + foundInDbIds.stream().map(UUID::toString).collect(Collectors.joining(",")));
    }
    throw new ResourceNotFoundException(String.join("; ", errors));
  }

  private boolean isCertificateNullOrEmpty(Map<?, ?> certificate) {
    return certificate == null || certificate.isEmpty();
  }
}
