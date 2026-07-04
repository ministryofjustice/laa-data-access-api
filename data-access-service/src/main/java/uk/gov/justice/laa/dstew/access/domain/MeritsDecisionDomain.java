package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.domain.enums.MeritsDecisionStatus;

/** Domain record representing the merits decision on a single proceeding. */
@Builder(toBuilder = true)
public record MeritsDecisionDomain(
    MeritsDecisionStatus decision, String reason, String justification, Instant modifiedAt) {}
