package uk.gov.justice.laa.dstew.access.command.application.decision;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a make-decision command with a single retry on concurrent-write failures. */
@Component
public class MakeApplicationDecisionUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final AssignmentInvariantService assignmentInvariantService;

  public MakeApplicationDecisionUseCase(
      RetryingCommandDispatcher dispatcher, AssignmentInvariantService assignmentInvariantService) {
    this.dispatcher = dispatcher;
    this.assignmentInvariantService = assignmentInvariantService;
  }

  /** Resolves assignment authority before dispatching the command to the application aggregate. */
  @AllowApiCaseworker
  public void execute(MakeApplicationDecisionCommand command) {
    assignmentInvariantService.validate(command);
    dispatcher.dispatch(command);
  }
}
