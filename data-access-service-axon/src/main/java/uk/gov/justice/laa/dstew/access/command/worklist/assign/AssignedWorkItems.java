package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of a work-item assignment: the addressed item followed by any other items assigned with
 * it (for example linked-group members). Items whose assignee did not change (for example
 * already-assigned or inactive members of a linked group) are not included.
 *
 * @param items the assigned items, addressed item first
 */
public record AssignedWorkItems(List<AssignedItem> items) {

  /**
   * One assigned work item.
   *
   * @param itemId the application ID, or the prior-authority submission ID for a PA item
   * @param laaReference the item's LAA reference, or the parent application's for a PA item
   */
  public record AssignedItem(UUID itemId, String laaReference) {}
}
