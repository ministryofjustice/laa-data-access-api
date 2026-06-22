package uk.gov.justice.laa.dstew.access.domain;

import lombok.Builder;

/** Read-model record for a proceeding scope limitation in the get-application response. */
@Builder(toBuilder = true)
public record ScopeLimitationReadModel(String scopeLimitation, String scopeDescription) {}
