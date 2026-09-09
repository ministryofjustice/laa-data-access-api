package uk.gov.justice.laa.dstew.access.controller.worklist;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.WorkListQueryApi;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.WorkListItemType;
import uk.gov.justice.laa.dstew.access.model.WorkListResponse;
import uk.gov.justice.laa.dstew.access.query.worklist.FindWorkListItemsQuery;
import uk.gov.justice.laa.dstew.access.usecase.worklist.WorkListQueryUseCase;

/** HTTP adapter for the replayable work-list projection. */
@RestController
public class WorkListQueryController implements WorkListQueryApi {

  private final WorkListQueryUseCase workListQueryUseCase;
  private final WorkListResponseMapper responseMapper;

  public WorkListQueryController(
      WorkListQueryUseCase workListQueryUseCase, WorkListResponseMapper responseMapper) {
    this.workListQueryUseCase = workListQueryUseCase;
    this.responseMapper = responseMapper;
  }

  /** Returns unassigned items by default or items assigned to the requested caseworker. */
  @Override
  public ResponseEntity<WorkListResponse> getWorkListItems(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      UUID assignedTo,
      Boolean unassigned,
      WorkListItemType itemType,
      Integer page,
      Integer pageSize) {
    return responseMapper.toResponse(
        workListQueryUseCase.findItems(
            new FindWorkListItemsQuery(
                assignedTo,
                itemType == null ? null : WorkItemType.valueOf(itemType.name()),
                unassigned,
                page,
                pageSize)));
  }
}
