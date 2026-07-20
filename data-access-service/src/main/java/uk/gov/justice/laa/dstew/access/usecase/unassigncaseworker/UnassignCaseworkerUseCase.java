package uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker.infrastructure.UnassignCaseworkerApplicationGateway;

/** Orchestrates unassigning a caseworker from an application. */
@RequiredArgsConstructor
public class UnassignCaseworkerUseCase {

  private final ApplicationGateway applicationGateway;
  private final UnassignCaseworkerApplicationGateway unassignCaseworkerApplicationGateway;
  private final SaveDomainEventService saveDomainEventService;

  /**
   * Unassigns a caseworker from the application identified by {@code command.applicationId()}.
   * Returns immediately without saving or firing a domain event if the application has no
   * caseworker assigned.
   *
   * @param command the input command
   * @throws ResourceNotFoundException if no application exists with the given ID
   */
  @Transactional
  @AllowApiCaseworker
  public void execute(UnassignCaseworkerCommand command) {
    ApplicationDomain applicationDomain =
        applicationGateway
            .findByApplicationId(command.applicationId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        String.format(
                            "No application found with id: %s", command.applicationId())));

    if (applicationDomain.caseworkerId() == null) {
      return;
    }

    unassignCaseworkerApplicationGateway.saveApplication(applicationDomain);

    saveDomainEventService.saveUnassignApplicationDomainEvent(
        applicationDomain.id(), null, command.eventDescription());
  }
}
