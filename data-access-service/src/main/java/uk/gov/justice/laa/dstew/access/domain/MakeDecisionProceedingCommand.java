package uk.gov.justice.laa.dstew.access.domain;

import java.util.UUID;
import lombok.Builder;

/** Domain record for per-proceeding decision input. */
@Builder(toBuilder = true)
public record MakeDecisionProceedingCommand(
    UUID proceedingId, MeritsDecisionOutcome meritsDecision, String reason, String justification) {}
