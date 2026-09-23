package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Validates application eligibility and dispatches a create-prior-authority-draft command, waiting
 * for the projection to confirm the draft is readable.
 */
@Component
public class CreatePriorAuthorityDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public CreatePriorAuthorityDraftUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /**
   * Validates the application is granted, dispatches the create-draft command, and waits for the
   * projection to become readable.
   *
   * @return {@code true} when the projection confirms the submission within the configured timeout;
   *     {@code false} on timeout — the command has still committed.
   * @throws RuntimeException propagated from validation if the application is not granted
   */
  @AllowApiCaseworker
  public boolean execute(CreatePriorAuthorityDraftCommand command) {
    dispatcher.dispatch(new ValidateApplicationGrantedCommand(command.applicationId()));
    dispatcher.dispatch(command);
    return true;
  }
}
