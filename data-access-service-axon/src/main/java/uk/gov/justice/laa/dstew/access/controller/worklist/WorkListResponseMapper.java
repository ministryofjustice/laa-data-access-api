package uk.gov.justice.laa.dstew.access.controller.worklist;

import java.time.ZoneOffset;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.model.WorkQueueItem;
import uk.gov.justice.laa.dstew.access.model.WorkQueueItemType;
import uk.gov.justice.laa.dstew.access.model.WorkQueueResponse;
import uk.gov.justice.laa.dstew.access.query.worklist.FindWorkListItemsResult;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadModel;

/** Maps active work-list projection rows to the public work-queue contract. */
@Component
public class WorkListResponseMapper {

  /** Maps one database-paged query result without consulting command-side routes. */
  public ResponseEntity<WorkQueueResponse> toResponse(FindWorkListItemsResult result) {
    WorkQueueResponse response = new WorkQueueResponse();
    response.setItems(result.items().stream().map(this::toItem).toList());

    PagingResponse paging = new PagingResponse();
    paging.setPage(result.requestedPage());
    paging.setPageSize(result.requestedPageSize());
    paging.setItemsReturned(result.items().size());
    paging.setTotalRecords(Math.toIntExact(result.totalElements()));
    response.setPaging(paging);
    return ResponseEntity.ok(response);
  }

  private WorkQueueItem toItem(WorkListItemReadModel item) {
    WorkQueueItem response = new WorkQueueItem();
    response.setItemId(item.getId().getItemId());
    response.setItemType(WorkQueueItemType.valueOf(item.getId().getItemType().name()));
    response.setApplicationId(item.getApplicationId());
    response.setParentApplicationId(item.getParentApplicationId());
    response.setAssignedTo(item.getAssigneeId());
    response.setAssignmentVersion(item.getAssignmentVersion());
    response.setAssignmentBoundaryType(
        WorkQueueItem.AssignmentBoundaryTypeEnum.valueOf(item.getAssignmentBoundaryType()));
    response.setGroupId(item.getGroupId());
    response.setSubmittedAt(
        item.getSubmittedAt() == null ? null : item.getSubmittedAt().atOffset(ZoneOffset.UTC));
    response.setLaaReference(item.getLaaReference());
    return response;
  }
}

