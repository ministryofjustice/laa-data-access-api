package uk.gov.justice.laa.dstew.access.command.synchronousapplication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;

/** Event establishing the complete initial state of a SynchronousApplication aggregate. */
public record SynchronousApplicationCreatedEvent(
    UUID applyApplicationId,
    String status,
    String laaReference,
    ApplicationContent applicationContent,
    List<SynchronousApplicationIndividual> individuals,
    int schemaVersion,
    String applicationType,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    List<SynchronousApplicationProceeding> proceedings,
    String serialisedRequest,
    Instant occurredAt) {}

