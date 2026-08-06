package uk.gov.justice.laa.dstew.access.controller.workqueue;

import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.model.WorkQueueItem;
import uk.gov.justice.laa.dstew.access.model.WorkQueueItemType;
import uk.gov.justice.laa.dstew.access.model.WorkQueueResponse;
import uk.gov.justice.laa.dstew.access.query.workqueue.FindWorkQueueItemsResult;
import uk.gov.justice.laa.dstew.access.query.workqueue.WorkQueueReadModel;

/** Maps a {@link FindWorkQueueItemsResult} to a {@link WorkQueueResponse}. */
@Component
public class GetWorkQueueResponseMapper {

  /** Builds the paginated response from the query result. */
  public ResponseEntity<WorkQueueResponse> toResponse(FindWorkQueueItemsResult result) {
    List<WorkQueueItem> items = result.items().stream().map(this::toItem).toList();

    PagingResponse paging = new PagingResponse();
    paging.setPage(result.requestedPage());
    paging.setPageSize(result.requestedPageSize());
    paging.setTotalRecords((int) result.totalElements());
    paging.setItemsReturned(items.size());

    WorkQueueResponse response = new WorkQueueResponse();
    response.setItems(items);
    response.setPaging(paging);

    return ResponseEntity.ok(response);
  }

  private WorkQueueItem toItem(WorkQueueReadModel model) {
    WorkQueueItem item = new WorkQueueItem();
    item.setItemId(model.getItemId());
    item.setItemType(WorkQueueItemType.valueOf(model.getItemType().name()));
    item.setAssignedTo(model.getAssignedTo());
    item.setLaaReference(model.getLaaReference());
    item.setSubmittedAt(
        model.getSubmittedAt() != null ? model.getSubmittedAt().atOffset(ZoneOffset.UTC) : null);
    return item;
  }
}
