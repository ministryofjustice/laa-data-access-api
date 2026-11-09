package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a submit-prior-authority-draft command once the parent application is granted. */
@Component
public class SubmitPriorAuthorityDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final PriorAuthorityDraftStore draftStore;

  /**
   * Constructs a new instance of SubmitPriorAuthorityDraftUseCase.
   *
   * @param dispatcher the command dispatcher used to send commands
   * @param draftStore the store used to retrieve prior authority drafts
   */
  public SubmitPriorAuthorityDraftUseCase(
      RetryingCommandDispatcher dispatcher, PriorAuthorityDraftStore draftStore) {
    this.dispatcher = dispatcher;
    this.draftStore = draftStore;
  }

  /**
   * Re-validates the application is still granted, then dispatches the submit command. The prior
   * authority is readable from its aggregate once the command commits.
   *
   * <p>The application may have been granted when the draft was started but could have changed
   * state since — so eligibility is re-checked here.
   *
   * @throws RuntimeException propagated from validation if the application is not granted
   */
  @AllowApiCaseworker
  public void submit(SubmitPriorAuthorityDraftCommand command) {
    PriorAuthorityDataPayload draft =
        draftStore
            .find(command.priorAuthorityId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority draft not found: " + command.priorAuthorityId()));
    dispatcher.dispatch(new ValidateApplicationGrantedCommand(draft.applicationId()));
    dispatcher.dispatch(command);
  }
}
