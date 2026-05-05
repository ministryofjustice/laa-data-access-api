package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Domain record representing a merits decision. */
@Builder(toBuilder = true)
public record MeritsDecisionDomain(
    UUID id,
    MeritsDecisionOutcome decision,
    String reason,
    String justification,
    Instant modifiedAt) {}
