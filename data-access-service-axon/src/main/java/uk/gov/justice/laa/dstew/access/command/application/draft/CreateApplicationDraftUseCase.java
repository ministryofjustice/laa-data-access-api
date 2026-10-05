package uk.gov.justice.laa.dstew.access.command.application.draft;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationProjectionExistsQuery;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches a create-application-draft command and waits for the projection to confirm the draft
 * is readable.
 */
@Component
public class CreateApplicationDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final SubscriptionProjectionGateway projectionGateway;

  public CreateApplicationDraftUseCase(
      RetryingCommandDispatcher dispatcher, SubscriptionProjectionGateway projectionGateway) {
    this.dispatcher = dispatcher;
    this.projectionGateway = projectionGateway;
  }

  /**
   * Dispatches the create-draft command and waits for its projection to become readable.
   *
   * @return {@code true} when the projection confirms the draft within the configured timeout;
   *     {@code false} on timeout — the command has still committed.
   */
  @AllowApiCaseworker
  public boolean execute(CreateApplicationDraftCommand command) {
    return projectionGateway.awaitProjection(
        new ApplicationProjectionExistsQuery(command.applicationId()),
        () -> dispatcher.dispatch(command));
  }
}
