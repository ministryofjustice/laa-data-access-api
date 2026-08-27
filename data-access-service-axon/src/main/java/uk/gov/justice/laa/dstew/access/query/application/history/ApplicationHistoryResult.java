package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.List;

/**
 * Aggregated result of an application history query, combining application and PA group results.
 */
public record ApplicationHistoryResult(
    List<ApplicationHistoryReadModel> applicationEvents,
    List<PriorAuthorityHistoryGroupResult> priorAuthorities) {}
