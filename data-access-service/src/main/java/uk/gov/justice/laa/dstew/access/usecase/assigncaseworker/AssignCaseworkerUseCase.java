package uk.gov.justice.laa.dstew.access.usecase.assigncaseworker;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.infrastructure.AssignCaseworkerApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.infrastructure.AssignCaseworkerCaseworkerGateway;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Orchestrates assigning a caseworker to one or more applications. */
@RequiredArgsConstructor
public class AssignCaseworkerUseCase {

  private final AssignCaseworkerApplicationGateway applicationGateway;
  private final AssignCaseworkerCaseworkerGateway caseworkerGateway;
  private final SaveDomainEventService saveDomainEventService;

  /**
   * Assigns the caseworker identified by {@code command.caseworkerId()} to each application in
   * {@code command.applicationIds()}. Skips the save if the application is already assigned to that
   * caseworker; always fires a domain event.
   *
   * @param command the input command
   */
  @AllowApiCaseworker
  @Transactional
  public void execute(AssignCaseworkerCommand command) {
    if (command.applicationIds().stream().anyMatch(Objects::isNull)) {
      throw new ValidationException(List.of("Request contains null values for ids"));
    }

    if (!caseworkerGateway.exists(command.caseworkerId())) {
      throw new ResourceNotFoundException(
          String.format("No caseworker found with id: %s", command.caseworkerId()));
    }

    List<UUID> distinctIds = command.applicationIds().stream().distinct().toList();
    List<ApplicationDomain> applications = applicationGateway.findAllByIds(distinctIds);

    checkForMissingApplications(distinctIds, applications);

    applications.forEach(app -> processApplication(app, command));
  }

  private void checkForMissingApplications(List<UUID> requestedIds, List<ApplicationDomain> found) {
    List<UUID> foundIds = found.stream().map(ApplicationDomain::id).toList();
    String missingIds =
        requestedIds.stream()
            .filter(id -> !foundIds.contains(id))
            .map(UUID::toString)
            .collect(Collectors.joining(","));
    if (!missingIds.isEmpty()) {
      throw new ResourceNotFoundException("No application found with ids: " + missingIds);
    }
  }

  private void processApplication(ApplicationDomain app, AssignCaseworkerCommand command) {
    if (!Objects.equals(app.caseworkerId(), command.caseworkerId())) {
      applicationGateway.save(app.toBuilder().caseworkerId(command.caseworkerId()).build());
    }
    saveDomainEventService.saveAssignApplicationDomainEvent(
        app.id(), command.caseworkerId(), command.eventDescription());
  }
}
