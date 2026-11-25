package uk.gov.justice.laa.dstew.access.controller.worklist;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.WorkListCommandApi;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemUseCase;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignedWorkItems;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignWorkItemUseCase;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignResponse;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignedItem;
import uk.gov.justice.laa.dstew.access.model.WorkListUnassignRequest;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedUserId;

/** HTTP adapter for generic work-item assignment commands. */
@RestController
public class WorkListCommandController implements WorkListCommandApi {
  private final AssignWorkItemUseCase assignWorkItemUseCase;
  private final UnassignWorkItemUseCase unassignWorkItemUseCase;
  private final AuthenticatedUserId authenticatedUserId;

  /** Creates the HTTP adapter with dedicated work-item assignment use cases. */
  public WorkListCommandController(
      AssignWorkItemUseCase assignWorkItemUseCase,
      UnassignWorkItemUseCase unassignWorkItemUseCase,
      AuthenticatedUserId authenticatedUserId) {
    this.assignWorkItemUseCase = assignWorkItemUseCase;
    this.unassignWorkItemUseCase = unassignWorkItemUseCase;
    this.authenticatedUserId = authenticatedUserId;
  }

  @Override
  public ResponseEntity<WorkListAssignResponse> assignWorkListItem(
      ServiceName serviceName, UUID itemId, WorkListAssignRequest request) {
    AssignedWorkItems assigned =
        assignWorkItemUseCase.execute(
            new AssignWorkItemCommand(
                itemId,
                authenticatedUserId.get(),
                request.getExpectedAssignmentVersion(),
                eventDescription(request.getEventHistory()),
                Instant.now()));
    return ResponseEntity.ok(
        new WorkListAssignResponse()
            .assignedItems(
                assigned.items().stream()
                    .map(
                        item ->
                            new WorkListAssignedItem()
                                .itemId(item.itemId())
                                .laaReference(item.laaReference()))
                    .toList()));
  }

  @Override
  public ResponseEntity<Void> unassignWorkListItem(
      ServiceName serviceName, UUID itemId, WorkListUnassignRequest request) {
    unassignWorkItemUseCase.execute(
        new UnassignWorkItemCommand(
            itemId,
            request.getExpectedAssignmentVersion(),
            eventDescription(request.getEventHistory()),
            Instant.now()));
    return ResponseEntity.ok().build();
  }

  private String eventDescription(EventHistoryRequest eventHistory) {
    return eventHistory == null ? null : eventHistory.getEventDescription();
  }
}
