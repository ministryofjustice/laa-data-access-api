package uk.gov.justice.laa.dstew.access.command.application;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;

/**
 * Dispatches a create-application command and waits for the projection to confirm the application
 * is readable.
 */
@Component
public class CreateApplicationUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final SubscriptionProjectionGateway projectionGateway;

  public CreateApplicationUseCase(
      RetryingCommandDispatcher dispatcher, SubscriptionProjectionGateway projectionGateway) {
    this.dispatcher = dispatcher;
    this.projectionGateway = projectionGateway;
  }

  /**
   * Dispatches the command and waits for the projection to become readable.
   *
   * @return {@code true} when the projection confirms the application within the configured
   *     timeout; {@code false} on timeout — the command has still committed.
   */
  public boolean execute(CreateApplicationCommand command) {
    return projectionGateway.awaitProjection(
        new FindApplicationByIdQuery(command.applicationId()),
        ApplicationReadModel.class,
        () -> dispatcher.dispatch(command));
  }
}
