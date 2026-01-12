package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.enums.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.enums.MatterType;

/**
 * Record representing extracted details from application content.
 */
@Builder
public record ParsedAppContentDetails(boolean autoGranted, CategoryOfLaw categoryOfLaw, MatterType matterType,
                                      Instant submittedAt, boolean useDelegatedFunctions) {
}