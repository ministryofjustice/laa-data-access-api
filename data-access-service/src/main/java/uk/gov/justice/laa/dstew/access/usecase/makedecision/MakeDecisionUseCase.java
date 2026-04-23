package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.CertificateGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.EnforceRole;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.RequiredRole;
import uk.gov.justice.laa.dstew.access.utils.VersionCheckHelper;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Use case for making a decision on an application. Wired via MakeDecisionConfig (no @Component).
 */
@Transactional
public class MakeDecisionUseCase {

  private final ApplicationGateway applicationGateway;
  private final CertificateGateway certificateGateway;
  private final DomainEventGateway domainEventGateway;

  /**
   * Constructs the use case with required dependencies.
   *
   * @param applicationGateway gateway for application persistence (aggregate load + save)
   * @param certificateGateway gateway for certificate persistence
   * @param domainEventGateway gateway for domain event publishing
   */
  public MakeDecisionUseCase(
      ApplicationGateway applicationGateway,
      CertificateGateway certificateGateway,
      DomainEventGateway domainEventGateway) {
    this.applicationGateway = applicationGateway;
    this.certificateGateway = certificateGateway;
    this.domainEventGateway = domainEventGateway;
  }

  /**
   * Executes the make decision use case.
   *
   * @param command the make decision command
   */
  @EnforceRole(anyOf = RequiredRole.API_CASEWORKER)
  public void execute(MakeDecisionCommand command) {
    // 1. Load full aggregate
    ApplicationDomain application = applicationGateway.loadById(command.applicationId());

    // 2. Optimistic locking check
    VersionCheckHelper.checkEntityVersionLocking(
        command.applicationId(), application.version(), command.applicationVersion());

    // 3. Business validation
    validateCommand(command);

    // 4. Validate proceedings exist in aggregate (in-memory, no gateway)
    Set<UUID> loadedIds =
        application.proceedings().stream().map(ProceedingDomain::id).collect(Collectors.toSet());
    List<UUID> missing =
        command.proceedings().stream()
            .map(MakeDecisionProceedingCommand::proceedingId)
            .filter(id -> !loadedIds.contains(id))
            .toList();
    if (!missing.isEmpty()) {
      throw new ResourceNotFoundException(
          "No proceeding found with id: " + missing + ". Not linked to application: " + missing);
    }

    // 5. Build updated proceedings with patched merits decisions
    List<ProceedingDomain> updatedProceedings =
        application.proceedings().stream().map(p -> patchProceeding(p, command)).toList();

    // 6. Build updated decision
    DecisionDomain decision =
        application.decision() != null
            ? application.decision().toBuilder().overallDecision(command.overallDecision()).build()
            : DecisionDomain.builder().overallDecision(command.overallDecision()).build();

    // 7. Build updated aggregate and save
    ApplicationDomain updated =
        application.toBuilder()
            .isAutoGranted(command.autoGranted())
            .proceedings(updatedProceedings)
            .decision(decision)
            .build();

    applicationGateway.save(updated);

    // 8. Certificate handling
    if (command.overallDecision() == OverallDecisionStatus.GRANTED) {
      certificateGateway.saveOrUpdate(command.applicationId(), command.certificate());
    } else if (command.overallDecision() == OverallDecisionStatus.REFUSED) {
      if (certificateGateway.existsByApplicationId(command.applicationId())) {
        certificateGateway.deleteByApplicationId(command.applicationId());
      }
    } else {
      throw new IllegalStateException("Unexpected value: " + command.overallDecision());
    }

    // 9. Domain event
    domainEventGateway.saveDecisionEvent(
        command.applicationId(),
        application.caseworkerId(),
        command.serialisedRequest(),
        command.eventDescription(),
        command.overallDecision());
  }

  private ProceedingDomain patchProceeding(
      ProceedingDomain proceeding, MakeDecisionCommand command) {
    MakeDecisionProceedingCommand cmd =
        command.proceedings().stream()
            .filter(p -> p.proceedingId().equals(proceeding.id()))
            .findFirst()
            .orElse(null);
    if (cmd == null) {
      return proceeding; // not included in this decision — leave untouched
    }
    MeritsDecisionDomain merits =
        proceeding.meritsDecision() != null
            ? proceeding.meritsDecision().toBuilder()
                .decision(cmd.meritsDecision())
                .reason(cmd.reason())
                .justification(cmd.justification())
                .build()
            : MeritsDecisionDomain.builder()
                .decision(cmd.meritsDecision())
                .reason(cmd.reason())
                .justification(cmd.justification())
                .build();
    return proceeding.toBuilder().meritsDecision(merits).build();
  }

  private void validateCommand(MakeDecisionCommand command) {
    if (command.proceedings() == null || command.proceedings().isEmpty()) {
      throw new ValidationException(
          List.of("The Make Decision request must contain at least one proceeding"));
    }
    if (command.overallDecision() == OverallDecisionStatus.GRANTED
        && isCertificateNullOrEmpty(command.certificate())) {
      throw new ValidationException(
          List.of(
              "The Make Decision request must contain a certificate when overallDecision is GRANTED"));
    }
    command
        .proceedings()
        .forEach(
            p -> {
              if (p.justification() == null || p.justification().isEmpty()) {
                throw new ValidationException(
                    List.of(
                        "The Make Decision request must contain a refusal justification for proceeding with id: "
                            + p.proceedingId()));
              }
            });
  }

  private boolean isCertificateNullOrEmpty(Map<String, Object> certificate) {
    return certificate == null || certificate.isEmpty();
  }
}
