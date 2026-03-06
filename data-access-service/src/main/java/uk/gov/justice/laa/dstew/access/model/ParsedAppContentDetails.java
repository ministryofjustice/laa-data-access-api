package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/**
 * Record representing extracted details from application content.
 */
@Builder
public record ParsedAppContentDetails(
    UUID applyApplicationId,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    List<Map<String, Object>> allLinkedApplications) {
}