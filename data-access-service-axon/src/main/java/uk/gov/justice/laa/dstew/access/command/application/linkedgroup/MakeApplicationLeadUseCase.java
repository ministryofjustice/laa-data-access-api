package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Thin controller-facing delegate for making an application the lead of its linked group. */
@Service
@RequiredArgsConstructor
public class MakeApplicationLeadUseCase {
  private final MakeApplicationLeadCoordinator coordinator;

  /** Executes the command to make a linked-group member its lead. */
  public void execute(MakeApplicationLeadCommand command) {
    coordinator.handle(command);
  }
}
