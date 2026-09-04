package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityPendingByPriorAuthorityIdQuery;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches a submit-prior-authority-draft command, waiting for the projection to confirm the
 * submission has transitioned to PENDING.
 */
@Component
public class SubmitPriorAuthorityDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final SubscriptionProjectionGateway projectionGateway;
  private final PriorAuthorityDraftStore draftStore;

  /**
   * Constructs a new instance of SubmitPriorAuthorityDraftUseCase.
   *
   * @param dispatcher the command dispatcher used to send commands
   * @param projectionGateway the gateway used to wait for projection updates
   * @param draftStore the store used to retrieve prior authority drafts
   */
  public SubmitPriorAuthorityDraftUseCase(
      RetryingCommandDispatcher dispatcher,
      SubscriptionProjectionGateway projectionGateway,
      PriorAuthorityDraftStore draftStore) {
    this.dispatcher = dispatcher;
    this.projectionGateway = projectionGateway;
    this.draftStore = draftStore;
  }

  /**
   * Re-validates the application is still granted, dispatches the submit command, and waits for the
   * projection to reflect the PENDING status.
   *
   * <p>The application may have been granted when the draft was started but could have changed
   * state since — since drafts can remain in progress indefinitely — so eligibility is re-checked
   * here, the same way {@link CreatePriorAuthorityUseCase} checks it before the original,
   * single-phase create.
   *
   * @return {@code true} when the projection confirms within the configured timeout; {@code false}
   *     on timeout — the command has still committed.
   * @throws RuntimeException propagated from validation if the application is not granted
   */
  @AllowApiCaseworker
  public boolean submit(SubmitPriorAuthorityDraftCommand command) {
    draftStore
        .find(command.priorAuthorityId())
        .ifPresent(
            draft ->
                dispatcher.dispatch(new ValidateApplicationGrantedCommand(draft.applicationId())));
    return projectionGateway.awaitProjection(
        new PriorAuthorityPendingByPriorAuthorityIdQuery(command.priorAuthorityId()),
        () -> dispatcher.dispatch(command));
  }
}
