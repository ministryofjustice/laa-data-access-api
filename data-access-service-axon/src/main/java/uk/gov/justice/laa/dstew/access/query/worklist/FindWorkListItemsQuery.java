package uk.gov.justice.laa.dstew.access.query.worklist;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.query.PaginationHelper;

/** Server-paged filters for the replayable active-work projection. */
public record FindWorkListItemsQuery(
    UUID assignedTo, WorkItemType itemType, Boolean unassigned, Integer page, Integer pageSize) {

  /** Validates mutually exclusive queue views and normalises one-based pagination. */
  public FindWorkListItemsQuery {
    if (assignedTo != null && Boolean.TRUE.equals(unassigned)) {
      throw new IllegalArgumentException("assignedTo and unassigned=true cannot be used together");
    }
    page = PaginationHelper.validatePage(page);
    pageSize = PaginationHelper.validatePageSize(pageSize);
  }
}

