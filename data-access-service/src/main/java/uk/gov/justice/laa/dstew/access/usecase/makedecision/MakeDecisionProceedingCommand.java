package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import java.util.UUID;
import lombok.Builder;

/**
 * Companion input record for a single proceeding in the make-decision command. No API model
 * imports.
 */
@Builder(toBuilder = true)
public record MakeDecisionProceedingCommand(
    UUID proceedingId, String decision, String reason, String justification) {}
