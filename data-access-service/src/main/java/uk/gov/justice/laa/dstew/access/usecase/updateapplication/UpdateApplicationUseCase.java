package uk.gov.justice.laa.dstew.access.usecase.updateapplication;

import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.enums.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Use case for updating an existing application. */
public class UpdateApplicationUseCase {

  private final ApplicationGateway applicationGateway;
  private final SaveDomainEventService saveDomainEventService;

  /**
   * Constructs the use case.
   *
   * @param applicationGateway the application persistence gateway
   * @param saveDomainEventService the domain event publisher
   */
  public UpdateApplicationUseCase(
      ApplicationGateway applicationGateway, SaveDomainEventService saveDomainEventService) {
    this.applicationGateway = applicationGateway;
    this.saveDomainEventService = saveDomainEventService;
  }

  /**
   * Executes the update operation.
   *
   * @param command the update command
   */
  @AllowApiCaseworker
  @Transactional
  public void execute(UpdateApplicationCommand command) {
    validateCommand(command);

    ApplicationDomain savedDomain =
        applicationGateway.update(command.id(), command.status(), command.applicationContent());

    saveDomainEventService.saveUpdateApplicationDomainEvent(savedDomain);
  }

  private void validateCommand(UpdateApplicationCommand command) {
    List<String> errors = new ArrayList<>();

    if (command.applicationContent() == null) {
      errors.add("Application content cannot be null");
    }

    if (command.applicationContent() != null && command.applicationContent().isEmpty()) {
      errors.add("Application content cannot be empty");
    }

    if (command.status() != null) {
      try {
        ApplicationStatus.valueOf(command.status());
      } catch (IllegalArgumentException ex) {
        errors.add("Invalid application status: " + command.status());
      }
    }
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }
}
