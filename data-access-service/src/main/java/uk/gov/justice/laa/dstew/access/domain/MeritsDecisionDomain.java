package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import lombok.Builder;

/** Domain record representing the merits decision on a single proceeding. */
@Builder(toBuilder = true)
public record MeritsDecisionDomain(
    String decision, String reason, String justification, Instant modifiedAt) {}
