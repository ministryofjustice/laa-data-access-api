package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;

/** Event establishing the complete initial state of a Application aggregate. */
public record ApplicationCreatedEvent(
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
