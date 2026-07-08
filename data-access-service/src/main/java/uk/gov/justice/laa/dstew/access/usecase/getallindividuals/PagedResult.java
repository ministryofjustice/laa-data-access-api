package uk.gov.justice.laa.dstew.access.usecase.getallindividuals;

import java.util.List;

/** Domain-owned pagination wrapper. */
public record PagedResult<T>(List<T> content, long totalElements) {}
