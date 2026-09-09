package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import org.springframework.stereotype.Service;

/** Controller-facing use case for unassigning a work item. */
@Service
public class UnassignWorkItemUseCase {
  private final UnassignWorkItemCommandHandler commandHandler;

  public UnassignWorkItemUseCase(UnassignWorkItemCommandHandler commandHandler) {
    this.commandHandler = commandHandler;
  }

  /** Unassigns a work item using its authoritative aggregate route. */
  public void execute(UnassignWorkItemCommand command) {
    commandHandler.handle(command);
  }
}
