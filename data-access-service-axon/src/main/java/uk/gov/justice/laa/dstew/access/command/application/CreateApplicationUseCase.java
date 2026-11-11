package uk.gov.justice.laa.dstew.access.command.application;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.ProjectionReadinessGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationSubmissionProjectedQuery;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches a create-application command and waits for the projection to confirm the application
 * is readable.
 */
@Component
public class CreateApplicationUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final ProjectionReadinessGateway projectionGateway;

  public CreateApplicationUseCase(
      RetryingCommandDispatcher dispatcher, ProjectionReadinessGateway projectionGateway) {
    this.dispatcher = dispatcher;
    this.projectionGateway = projectionGateway;
  }

  /**
   * Dispatches the command and waits for the projection to become readable.
   *
   * @return {@code true} when the projection confirms the application within the configured
   *     timeout; {@code false} on timeout — the command has still committed.
   */
  @AllowApiCaseworker
  public boolean execute(CreateApplicationCommand command) {
    return projectionGateway.awaitProjection(
        new ApplicationSubmissionProjectedQuery(command.applicationId()),
        () -> dispatcher.dispatch(command));
  }
}
