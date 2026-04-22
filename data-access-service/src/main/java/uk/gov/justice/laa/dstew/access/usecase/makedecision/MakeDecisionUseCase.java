package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MakeDecisionProceedingCommand;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.CertificateGateway;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.DecisionGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ProceedingGateway;
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
  private final ProceedingGateway proceedingGateway;
  private final DecisionGateway decisionGateway;
  private final CertificateGateway certificateGateway;
  private final DomainEventGateway domainEventGateway;

  /**
   * Constructs the use case with required dependencies.
   *
   * @param applicationGateway gateway for application persistence
   * @param proceedingGateway gateway for proceeding persistence
   * @param decisionGateway gateway for decision persistence
   * @param certificateGateway gateway for certificate persistence
   * @param domainEventGateway gateway for domain event publishing
   */
  public MakeDecisionUseCase(
      ApplicationGateway applicationGateway,
      ProceedingGateway proceedingGateway,
      DecisionGateway decisionGateway,
      CertificateGateway certificateGateway,
      DomainEventGateway domainEventGateway) {
    this.applicationGateway = applicationGateway;
    this.proceedingGateway = proceedingGateway;
    this.decisionGateway = decisionGateway;
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
    // 1. Load application
    ApplicationDomain application = applicationGateway.findById(command.applicationId());

    // 2. Optimistic locking check
    VersionCheckHelper.checkEntityVersionLocking(
        command.applicationId(), application.version(), command.applicationVersion());

    // 3. Business validation
    validateCommand(command);

    // 4. Validate proceedings existence and linkage
    proceedingGateway.findAllByIds(
        command.applicationId(),
        command.proceedings().stream().map(MakeDecisionProceedingCommand::proceedingId).toList());

    // 5. Update application autoGranted flag
    applicationGateway.updateAutoGranted(command.applicationId(), command.autoGranted());

    // 6. Upsert decision + merits; link to application if new
    DecisionDomain existingDecision = decisionGateway.findByApplicationId(command.applicationId());
    DecisionDomain toSave = buildDecisionDomain(existingDecision, command);
    decisionGateway.saveAndLink(command.applicationId(), toSave);

    // 7. Certificate handling
    if (command.overallDecision() == OverallDecisionStatus.GRANTED) {
      certificateGateway.saveOrUpdate(command.applicationId(), command.certificate());
    } else if (command.overallDecision() == OverallDecisionStatus.REFUSED) {
      if (certificateGateway.existsByApplicationId(command.applicationId())) {
        certificateGateway.deleteByApplicationId(command.applicationId());
      }
    } else {
      throw new IllegalStateException("Unexpected value: " + command.overallDecision());
    }

    // 8. Domain event
    domainEventGateway.saveDecisionEvent(
        command.applicationId(),
        application.caseworkerId(),
        command.serialisedRequest(),
        command.eventDescription(),
        command.overallDecision());
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

  private DecisionDomain buildDecisionDomain(DecisionDomain existing, MakeDecisionCommand command) {
    Set<MeritsDecisionDomain> meritsDecisions =
        command.proceedings().stream()
            .map(
                p -> {
                  UUID existingId =
                      existing != null
                          ? findExistingMeritsId(existing.meritsDecisions(), p.proceedingId())
                          : null;
                  return MeritsDecisionDomain.builder()
                      .id(existingId)
                      .proceedingId(p.proceedingId())
                      .decision(p.meritsDecision())
                      .reason(p.reason())
                      .justification(p.justification())
                      .build();
                })
            .collect(Collectors.toSet());

    return DecisionDomain.builder()
        .id(existing != null ? existing.id() : null)
        .overallDecision(command.overallDecision())
        .meritsDecisions(meritsDecisions)
        .build();
  }

  private UUID findExistingMeritsId(Set<MeritsDecisionDomain> existing, UUID proceedingId) {
    if (existing == null) {
      return null;
    }
    return existing.stream()
        .filter(m -> proceedingId.equals(m.proceedingId()))
        .map(MeritsDecisionDomain::id)
        .findFirst()
        .orElse(null);
  }
}
