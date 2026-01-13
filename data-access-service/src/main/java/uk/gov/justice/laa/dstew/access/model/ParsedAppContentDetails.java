package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Record representing extracted details from application content.
 */
@Builder
public record ParsedAppContentDetails(
    UUID applyApplicationId,
    boolean autoGranted,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Instant submittedAt,
    boolean useDelegatedFunctions) {
}