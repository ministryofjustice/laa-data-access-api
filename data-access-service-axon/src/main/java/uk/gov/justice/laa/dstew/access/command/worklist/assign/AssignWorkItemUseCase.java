package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import org.springframework.stereotype.Service;

/** Controller-facing use case for assigning a work item. */
@Service
public class AssignWorkItemUseCase {
  private final AssignWorkItemCoordinator coordinator;

  /** Creates the use case with its transactional coordinator. */
  public AssignWorkItemUseCase(AssignWorkItemCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  /** Assigns a work item using its authoritative aggregate route. */
  public void execute(AssignWorkItemCommand command) {
    coordinator.handle(command);
  }
}
