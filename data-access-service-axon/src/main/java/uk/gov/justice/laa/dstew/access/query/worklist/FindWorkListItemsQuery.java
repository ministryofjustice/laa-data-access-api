package uk.gov.justice.laa.dstew.access.query.worklist;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.query.PaginationHelper;

/** Server-paged filters for the replayable active-work projection. */
public record FindWorkListItemsQuery(
    Boolean assignedToMe,
    WorkItemType itemType,
    Boolean unassigned,
    Integer page,
    Integer pageSize,
    UUID authenticatedUserId) {

  /** Defaults to open applications and normalises pagination. */
  public FindWorkListItemsQuery {
    if (assignedToMe == null) {
      assignedToMe = false;
    }
    if (unassigned == null) {
      unassigned = true;
    }
    if (!assignedToMe && !unassigned) {
      throw new IllegalArgumentException("assignedToMe and unassigned cannot both be false");
    }
    if (assignedToMe && authenticatedUserId == null) {
      throw new IllegalArgumentException(
          "authenticatedUserId is required when assignedToMe is true");
    }
    page = PaginationHelper.validatePage(page);
    pageSize = PaginationHelper.validatePageSize(pageSize);
  }
}
