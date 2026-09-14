package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import org.springframework.stereotype.Service;

/** Controller-facing use case for assigning a work item. */
@Service
public class AssignWorkItemUseCase {
  private final AssignWorkItemCommandHandler commandHandler;

  public AssignWorkItemUseCase(AssignWorkItemCommandHandler commandHandler) {
    this.commandHandler = commandHandler;
  }

  /** Assigns a work item using its authoritative aggregate route. */
  public void execute(AssignWorkItemCommand command) {
    commandHandler.handle(command);
  }
}
