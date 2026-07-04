package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.domain.enums.MeritsDecisionStatus;

/** Companion input record for a single proceeding in the make-decision command. */
@Builder(toBuilder = true)
public record MakeDecisionProceedingCommand(
    UUID proceedingId, MeritsDecisionStatus decision, String reason, String justification) {}
