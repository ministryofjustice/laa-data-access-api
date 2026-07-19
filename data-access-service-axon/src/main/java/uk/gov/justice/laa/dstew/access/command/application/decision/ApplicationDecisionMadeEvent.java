package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.time.Instant;
import java.util.UUID;

/** Thin event recording that an Application decision was stored in an immutable data version. */
public record ApplicationDecisionMadeEvent(
    UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    String overallDecision,
    Boolean autoGranted,
    Instant occurredAt) {}
