package uk.gov.justice.laa.dstew.access.domain;

import lombok.Builder;

/** Domain record for a single opponent extracted from application merits data. */
@Builder(toBuilder = true)
public record OpponentDomain(
    String opponentType, String firstName, String lastName, String organisationName) {}
