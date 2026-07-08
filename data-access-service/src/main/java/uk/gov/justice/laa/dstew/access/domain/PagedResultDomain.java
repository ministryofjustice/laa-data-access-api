package uk.gov.justice.laa.dstew.access.domain;

import java.util.List;

/** Carries a page of domain results and the total matching element count. */
public record PagedResultDomain<T>(List<T> content, long totalElements) {}
