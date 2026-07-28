package uk.gov.justice.laa.dstew.access.query.workitem;

import java.util.List;

/** A single page of work items plus its pagination metadata. */
public record WorkloadPage(
    List<WorkItemView> content, int page, int size, long totalElements, int totalPages) {}
