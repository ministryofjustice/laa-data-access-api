package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.util.UUID;

/** Decision requested for a single proceeding. */
public record MakeDecisionProceeding(
    UUID proceedingId, String decision, String reason, String justification) {}
