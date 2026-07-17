package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;

/**
 * Command to submit an application. Carries no personal data: the body has already been persisted
 * to the deletable {@code submissions} table by the application layer and is referenced here only
 * by {@code contentId}. The remaining fields are non-PII structural metadata.
 */
public record CreateApplicationCommand(
    @TargetAggregateIdentifier UUID applyApplicationId,
    UUID contentId,
    String status,
    String laaReference,
    int schemaVersion,
    String applicationType,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType) {}
