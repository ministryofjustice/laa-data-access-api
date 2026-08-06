package uk.gov.justice.laa.dstew.access.query.workqueue;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.query.PaginationHelper;

/**
 * Query to retrieve a paginated list of work queue items.
 *
 * <p>Exactly one of {@code unassigned} (true) or {@code assignedTo} (non-null) should be set.
 * {@code unassigned=true} returns the Open Applications view; {@code assignedTo={uuid}} returns the
 * Personal Queue for that caseworker.
 */
public record FindWorkQueueItemsQuery(UUID assignedTo, Boolean unassigned, Integer page, Integer pageSize) {

  /** Resolves pagination defaults and validates constraints. */
  public FindWorkQueueItemsQuery {
    page = PaginationHelper.validatePage(page);
    pageSize = PaginationHelper.validatePageSize(pageSize);
  }
}
