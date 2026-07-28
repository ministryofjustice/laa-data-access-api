package uk.gov.justice.laa.dstew.access.controller.workitem;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.query.workitem.GetWorkloadQuery;
import uk.gov.justice.laa.dstew.access.query.workitem.WorkType;
import uk.gov.justice.laa.dstew.access.query.workitem.WorkloadPage;

/**
 * HTTP query adapter for the caseworker workload (Decide's queues).
 *
 * <p>Returns a filtered, paginated page of work items. Filter by {@code work_type} for Decide's two
 * lists, by {@code assigned_caseworker_id} for a caseworker's queue, or {@code unassigned=true} for
 * the pool of items awaiting assignment.
 */
@RestController
@RequestMapping("/api/v0/workload")
@Validated
public class WorkloadQueryController {

  private final QueryGateway queryGateway;

  /** Wires the Axon query gateway. */
  public WorkloadQueryController(QueryGateway queryGateway) {
    this.queryGateway = queryGateway;
  }

  /** Returns a page of work items matching the supplied filters. */
  @GetMapping
  public ResponseEntity<WorkloadPage> getWorkload(
      @RequestParam(name = "work_type", required = false) WorkType workType,
      @RequestParam(name = "assigned_caseworker_id", required = false) UUID assignedCaseworkerId,
      @RequestParam(name = "unassigned", required = false, defaultValue = "false")
          boolean unassigned,
      @RequestParam(name = "page", required = false, defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", required = false, defaultValue = "20") @Min(1) @Max(200)
          int size) {
    WorkloadPage result =
        queryGateway
            .query(
                new GetWorkloadQuery(workType, assignedCaseworkerId, unassigned, page, size),
                ResponseTypes.instanceOf(WorkloadPage.class))
            .join();
    return ResponseEntity.ok(result);
  }
}
