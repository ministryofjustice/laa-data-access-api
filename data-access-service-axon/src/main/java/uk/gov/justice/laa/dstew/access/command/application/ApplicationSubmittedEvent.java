package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;

/**
 * Event marking that an application was submitted. Emitted either directly (a fresh aggregate is
 * created straight into the submitted state) or as a transition from an existing draft.
 */
public record ApplicationSubmittedEvent(
    UUID applyApplicationId,
    String status,
    String laaReference,
    ApplicationContent applicationContent,
    List<ApplicationIndividual> individuals,
    int schemaVersion,
    String applicationType,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    List<ApplicationProceeding> proceedings,
    String serialisedRequest,
    Instant occurredAt) {}
