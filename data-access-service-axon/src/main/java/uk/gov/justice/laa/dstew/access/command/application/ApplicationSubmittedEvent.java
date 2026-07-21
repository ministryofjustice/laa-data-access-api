package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;

/**
 * Pointer event marking that an application was submitted. Emitted either directly (a fresh
 * aggregate is created straight into the submitted state) or as a transition from an existing
 * draft.
 *
 * <p>Carries no personal data: the submitted body (content, individuals, proceedings) lives in the
 * deletable {@code submissions} table, referenced here only by {@code contentId}. All remaining
 * fields are non-PII structural metadata used by the current-state projection.
 */
public record ApplicationSubmittedEvent(
    UUID applyApplicationId,
    UUID contentId,
    String status,
    String laaReference,
    int schemaVersion,
    String applicationType,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Instant occurredAt) {}
