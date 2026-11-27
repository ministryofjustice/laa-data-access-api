package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.ProjectionReadinessGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationProjectionExistsQuery;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches a v2 create-draft command and waits for the projection to confirm the draft is
 * readable.
 */
@Component
public class CreateApplicationDraftV2UseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final ProjectionReadinessGateway projectionGateway;

  public CreateApplicationDraftV2UseCase(
      RetryingCommandDispatcher dispatcher, ProjectionReadinessGateway projectionGateway) {
    this.dispatcher = dispatcher;
    this.projectionGateway = projectionGateway;
  }

  /** Dispatches the create command and reports whether the projection became readable. */
  @AllowApiCaseworker
  public ApplicationDraftSaveResult execute(CreateApplicationDraftV2Command command) {
    AtomicLong draftVersion = new AtomicLong();
    boolean projected =
        projectionGateway.awaitProjection(
            new ApplicationProjectionExistsQuery(command.applicationId()),
            () -> draftVersion.set(dispatcher.dispatch(command, Long.class)));
    return new ApplicationDraftSaveResult(projected, draftVersion.get());
  }
}
