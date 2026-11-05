package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import org.springframework.stereotype.Service;

/** Controller-facing use case for unassigning a work item. */
@Service
public class UnassignWorkItemUseCase {
  private final UnassignWorkItemCoordinator coordinator;

  /** Creates the use case with its transactional coordinator. */
  public UnassignWorkItemUseCase(UnassignWorkItemCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  /** Unassigns a work item using its authoritative aggregate route. */
  public void execute(UnassignWorkItemCommand command) {
    coordinator.handle(command);
  }
}
