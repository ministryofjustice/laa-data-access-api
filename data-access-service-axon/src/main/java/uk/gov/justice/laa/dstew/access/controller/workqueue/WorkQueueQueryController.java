package uk.gov.justice.laa.dstew.access.controller.workqueue;

import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.model.WorkQueueResponse;
import uk.gov.justice.laa.dstew.access.query.workqueue.FindWorkQueueItemsQuery;
import uk.gov.justice.laa.dstew.access.query.workqueue.FindWorkQueueItemsResult;

/** HTTP query adapter for the work queue. */
@RestController
@RequestMapping("/api/v0/work-queue")
public class WorkQueueQueryController {

  private final QueryGateway queryGateway;
  private final GetWorkQueueResponseMapper responseMapper;

  public WorkQueueQueryController(
      QueryGateway queryGateway, GetWorkQueueResponseMapper responseMapper) {
    this.queryGateway = queryGateway;
    this.responseMapper = responseMapper;
  }

  /**
   * Returns a paginated work queue.
   *
   * <p>Pass {@code ?unassigned=true} for the Open Applications view (items with no assigned
   * caseworker). Pass {@code ?assignedTo={uuid}} for a caseworker's Personal Queue.
   */
  @GetMapping
  public ResponseEntity<WorkQueueResponse> getWorkQueueItems(
      @RequestParam(required = false) UUID assignedTo,
      @RequestParam(required = false) Boolean unassigned,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    FindWorkQueueItemsResult result =
        queryGateway
            .query(
                new FindWorkQueueItemsQuery(assignedTo, unassigned, page, pageSize),
                FindWorkQueueItemsResult.class)
            .join();
    return responseMapper.toResponse(result);
  }
}
