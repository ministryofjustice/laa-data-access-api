package uk.gov.justice.laa.dstew.access.controller.workitem;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.service.workitem.WorkItemAssignmentService;

/**
 * HTTP command adapter for assigning caseworkers to work items. The endpoint is polymorphic over
 * the work-item kind: the {@code workItemId} is resolved to its owning aggregate (an application or
 * a prior authority member), which enforces the one-active-caseworker invariant.
 */
@RestController
@RequestMapping("/api/v0/work-items")
public class WorkItemCommandController {

  private final WorkItemAssignmentService assignmentService;

  public WorkItemCommandController(WorkItemAssignmentService assignmentService) {
    this.assignmentService = assignmentService;
  }

  /** Assigns a caseworker to the work item and returns 204 No Content. */
  @PostMapping("/{workItemId}/assign")
  public ResponseEntity<Void> assign(
      @PathVariable UUID workItemId, @Valid @RequestBody AssignCaseworkerRequest request) {
    assignmentService.assign(workItemId, request.caseworkerId());
    return ResponseEntity.noContent().build();
  }

  /** Clears the caseworker assignment on the work item and returns 204 No Content. */
  @DeleteMapping("/{workItemId}/assignment")
  public ResponseEntity<Void> unassign(@PathVariable UUID workItemId) {
    assignmentService.unassign(workItemId);
    return ResponseEntity.noContent().build();
  }
}
