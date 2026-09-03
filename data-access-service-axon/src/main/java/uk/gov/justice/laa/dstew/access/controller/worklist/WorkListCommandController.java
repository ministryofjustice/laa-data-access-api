package uk.gov.justice.laa.dstew.access.controller.worklist;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.api.WorkListCommandApi;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemUseCase;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignWorkItemUseCase;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListUnassignRequest;

/** HTTP adapter for generic work-item assignment commands. */
@RestController
public class WorkListCommandController implements WorkListCommandApi {
  private final AssignWorkItemUseCase assignWorkItemUseCase;
  private final UnassignWorkItemUseCase unassignWorkItemUseCase;
  private final ObjectMapper objectMapper;

  /** Creates the HTTP adapter with dedicated work-item assignment use cases. */
  public WorkListCommandController(
      AssignWorkItemUseCase assignWorkItemUseCase,
      UnassignWorkItemUseCase unassignWorkItemUseCase,
      ObjectMapper objectMapper) {
    this.assignWorkItemUseCase = assignWorkItemUseCase;
    this.unassignWorkItemUseCase = unassignWorkItemUseCase;
    this.objectMapper = objectMapper;
  }

  @Override
  public ResponseEntity<Void> assignWorkListItem(
      ServiceName serviceName, UUID itemId, WorkListAssignRequest request) {
    assignWorkItemUseCase.execute(
        new AssignWorkItemCommand(
            itemId,
            request.getCaseworkerId(),
            request.getExpectedAssignmentVersion(),
            serialise(request),
            eventDescription(request.getEventHistory()),
            Instant.now()));
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> unassignWorkListItem(
      ServiceName serviceName, UUID itemId, WorkListUnassignRequest request) {
    unassignWorkItemUseCase.execute(
        new UnassignWorkItemCommand(
            itemId,
            request.getExpectedAssignmentVersion(),
            serialise(request),
            eventDescription(request.getEventHistory()),
            Instant.now()));
    return ResponseEntity.ok().build();
  }

  private String eventDescription(
      uk.gov.justice.laa.dstew.access.model.EventHistoryRequest eventHistory) {
    return eventHistory == null ? null : eventHistory.getEventDescription();
  }

  private String serialise(Object request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "Unable to serialise work-list assignment request", exception);
    }
  }
}
