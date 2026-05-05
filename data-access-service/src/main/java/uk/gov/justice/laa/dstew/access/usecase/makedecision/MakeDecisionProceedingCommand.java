package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionOutcome;

/** Per-proceeding input record for the makeDecision use case. */
@Builder(toBuilder = true)
public record MakeDecisionProceedingCommand(
    UUID proceedingId, MeritsDecisionOutcome meritsDecision, String reason, String justification) {}
