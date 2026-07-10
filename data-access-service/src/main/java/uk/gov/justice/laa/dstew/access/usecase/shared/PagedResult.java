package uk.gov.justice.laa.dstew.access.usecase.shared;

import java.util.List;

/** Carries a page of results and the total matching element count. */
public record PagedResult<T>(List<T> content, long totalElements) {}
