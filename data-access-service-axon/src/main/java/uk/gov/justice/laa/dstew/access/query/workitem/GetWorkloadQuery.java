package uk.gov.justice.laa.dstew.access.query.workitem;

import java.util.UUID;

/**
 * Query for a filtered, paginated page of caseworker work items.
 *
 * @param workType optional filter to one kind of work item (Decide's two lists)
 * @param assignedCaseworkerId optional filter to a caseworker's queue
 * @param unassigned when {@code true}, restrict to the unassigned pool (no caseworker yet)
 * @param page zero-based page index
 * @param size page size
 */
public record GetWorkloadQuery(
    WorkType workType, UUID assignedCaseworkerId, boolean unassigned, int page, int size) {}
