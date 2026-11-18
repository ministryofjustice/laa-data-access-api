package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Thin controller-facing delegate for explicit application linking. */
@Service
@RequiredArgsConstructor
public class LinkApplicationUseCase {
  private final LinkApplicationCoordinator coordinator;

  /** Executes the explicit application-link command. */
  public void execute(LinkApplicationCommand command) {
    coordinator.handle(command);
  }
}
