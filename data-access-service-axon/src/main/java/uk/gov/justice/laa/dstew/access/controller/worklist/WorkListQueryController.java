package uk.gov.justice.laa.dstew.access.controller.worklist;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.WorkQueueQueryApi;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.model.WorkQueueItemType;
import uk.gov.justice.laa.dstew.access.model.WorkQueueResponse;
import uk.gov.justice.laa.dstew.access.query.worklist.FindWorkListItemsQuery;
import uk.gov.justice.laa.dstew.access.usecase.worklist.WorkListQueryUseCase;

/** HTTP adapter for the replayable work-list projection. */
@RestController
public class WorkListQueryController implements WorkQueueQueryApi {

  private final WorkListQueryUseCase workListQueryUseCase;
  private final WorkListResponseMapper responseMapper;

  public WorkListQueryController(
      WorkListQueryUseCase workListQueryUseCase, WorkListResponseMapper responseMapper) {
    this.workListQueryUseCase = workListQueryUseCase;
    this.responseMapper = responseMapper;
  }

  /** Returns an open or personal active-work view, filtered and paged by the database. */
  @Override
  public ResponseEntity<WorkQueueResponse> getWorkQueueItems(
      UUID assignedTo, Boolean unassigned, WorkQueueItemType itemType, Integer page, Integer pageSize) {
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

