package uk.gov.justice.laa.dstew.access.usecase.updateapplication;

import jakarta.transaction.Transactional;
import java.util.List;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
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

    ApplicationDomain existingDomain = applicationGateway.findById(command.id());
    ApplicationDomain updatedDomain =
        existingDomain.toBuilder()
            .status(command.status() != null ? command.status() : existingDomain.status())
            .applicationContent(command.applicationContent())
            .build();
    ApplicationDomain savedDomain = applicationGateway.update(command.id(), updatedDomain);

    saveDomainEventService.saveUpdateApplicationDomainEvent(savedDomain);
  }

  private void validateCommand(UpdateApplicationCommand updateApplicationCommand) {
    if (updateApplicationCommand.applicationContent() == null) {
      throw new ValidationException(List.of("Application content cannot be null"));
    }
    if (updateApplicationCommand.applicationContent().isEmpty()) {
      throw new ValidationException(List.of("Application content cannot be empty"));
    }
  }
}
