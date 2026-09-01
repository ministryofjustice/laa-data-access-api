package uk.gov.justice.laa.dstew.access.controller.worklist;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.api.WorkListCommandApi;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentCommandHandler;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListUnassignRequest;

/** HTTP adapter for generic work-item assignment commands. */
@RestController
public class WorkListCommandController implements WorkListCommandApi {
  private final WorkItemAssignmentCommandHandler assignmentHandler;
  private final ObjectMapper objectMapper;

  public WorkListCommandController(
      WorkItemAssignmentCommandHandler assignmentHandler, ObjectMapper objectMapper) {
    this.assignmentHandler = assignmentHandler;
    this.objectMapper = objectMapper;
  }

  @Override
  public ResponseEntity<Void> assignWorkListItem(
      ServiceName serviceName, UUID itemId, WorkListAssignRequest request) {
    assignmentHandler.assign(
        itemId,
        request.getCaseworkerId(),
        request.getExpectedAssignmentVersion(),
        serialise(request),
        eventDescription(request.getEventHistory()),
        Instant.now());
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> unassignWorkListItem(
      ServiceName serviceName, UUID itemId, WorkListUnassignRequest request) {
    assignmentHandler.unassign(
        itemId,
        request.getExpectedAssignmentVersion(),
        serialise(request),
        eventDescription(request.getEventHistory()),
        Instant.now());
    return ResponseEntity.ok().build();
  }

  private String eventDescription(uk.gov.justice.laa.dstew.access.model.EventHistoryRequest eventHistory) {
    return eventHistory == null ? null : eventHistory.getEventDescription();
  }

  private String serialise(Object request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise work-list assignment request", exception);
    }
  }
}
