package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityBySubmissionIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches a submit-prior-authority-draft command, waiting for the projection to confirm the
 * submission has transitioned to PENDING.
 */
@Component
public class SubmitPriorAuthorityDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final SubscriptionProjectionGateway projectionGateway;

  public SubmitPriorAuthorityDraftUseCase(
      RetryingCommandDispatcher dispatcher, SubscriptionProjectionGateway projectionGateway) {
    this.dispatcher = dispatcher;
    this.projectionGateway = projectionGateway;
  }

  /**
   * Dispatches the submit command and waits for the projection to reflect the PENDING status.
   *
   * @return {@code true} when the projection confirms within the configured timeout; {@code false}
   *     on timeout — the command has still committed.
   */
  @AllowApiCaseworker
  public boolean submit(SubmitPriorAuthorityDraftCommand command) {
    return projectionGateway.awaitProjection(
        new FindPriorAuthorityBySubmissionIdQuery(command.submissionId()),
        PriorAuthorityReadModel.class,
        () -> dispatcher.dispatch(command));
  }
}
