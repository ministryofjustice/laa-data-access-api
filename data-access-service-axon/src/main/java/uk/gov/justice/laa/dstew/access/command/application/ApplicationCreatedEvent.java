package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;

/** Event establishing the complete initial state of an Application aggregate. */
public record ApplicationCreatedEvent(
    UUID applicationId,
    String status,
    String laaReference,
    ApplicationContent applicationContent,
    List<ApplicationIndividual> individuals,
    int schemaVersion,
    String applicationType,
    UUID applyApplicationId,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    List<ApplicationProceeding> proceedings,
    String serialisedRequest,
    Instant occurredAt,
    UUID leadApplicationId) {} // nullable — null for standalone (lead) applications;
                              // backward-compatible via Jackson
