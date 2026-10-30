package uk.gov.justice.laa.dstew.access.command.application.decision;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a make-decision command with a single retry on concurrent-write failures. */
@Component
@RequiredArgsConstructor
public class MakeApplicationDecisionUseCase {
  private final MakeApplicationDecisionCoordinator coordinator;

  /** Dispatches the command to the application aggregate for authoritative validation. */
  @AllowApiCaseworker
  public void execute(MakeApplicationDecisionCommand command) {
    coordinator.handle(command);
  }
}
