package uk.gov.justice.laa.dstew.access.domain;

import lombok.Builder;

/** Domain record for a proceeding scope limitation. */
@Builder(toBuilder = true)
public record ScopeLimitationDomain(String scopeLimitation, String scopeDescription) {}
