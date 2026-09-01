package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityBySubmissionIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches a save-prior-authority-draft command, waiting for the projection to confirm the
 * submission is readable on initial creation.
 */
@Component
public class SavePriorAuthorityDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final SubscriptionProjectionGateway projectionGateway;

  public SavePriorAuthorityDraftUseCase(
      RetryingCommandDispatcher dispatcher, SubscriptionProjectionGateway projectionGateway) {
    this.dispatcher = dispatcher;
    this.projectionGateway = projectionGateway;
  }

  /**
   * Dispatches the save-draft command and waits for the projection to become readable.
   *
   * @return {@code true} when the projection confirms the submission within the configured timeout;
   *     {@code false} on timeout — the command has still committed.
   */
  @AllowApiCaseworker
  public boolean create(SavePriorAuthorityDraftCommand command) {
    return projectionGateway.awaitProjection(
        new FindPriorAuthorityBySubmissionIdQuery(command.submissionId()),
        PriorAuthorityReadModel.class,
        () -> dispatcher.dispatch(command));
  }

  /** Dispatches the save-draft command to update an existing draft submission. */
  @AllowApiCaseworker
  public void update(SavePriorAuthorityDraftCommand command) {
    dispatcher.dispatch(command);
  }
}
