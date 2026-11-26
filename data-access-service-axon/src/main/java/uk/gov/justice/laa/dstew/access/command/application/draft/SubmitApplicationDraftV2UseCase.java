package uk.gov.justice.laa.dstew.access.command.application.draft;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.ProjectionReadinessGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationSubmissionProjectedQuery;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches a v2 submit-draft command, waiting for the projection to confirm the Application is
 * readable.
 */
@Component
public class SubmitApplicationDraftV2UseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final ProjectionReadinessGateway projectionGateway;

  public SubmitApplicationDraftV2UseCase(
      RetryingCommandDispatcher dispatcher, ProjectionReadinessGateway projectionGateway) {
    this.dispatcher = dispatcher;
    this.projectionGateway = projectionGateway;
  }

  /** Dispatches the submit command and reports whether the projection became readable. */
  @AllowApiCaseworker
  public boolean submit(SubmitApplicationDraftV2Command command) {
    return projectionGateway.awaitProjection(
        new ApplicationSubmissionProjectedQuery(command.applicationId()),
        () -> dispatcher.dispatch(command));
  }
}
