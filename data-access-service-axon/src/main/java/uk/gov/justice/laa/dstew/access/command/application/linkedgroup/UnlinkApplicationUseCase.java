package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Thin controller-facing delegate for removing an application from its linked group. */
@Service
@RequiredArgsConstructor
public class UnlinkApplicationUseCase {
  private final UnlinkApplicationCommandHandler commandHandler;

  public void execute(UnlinkApplicationCommand command) {
    commandHandler.handle(command);
  }
}
