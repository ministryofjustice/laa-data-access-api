package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Domain record representing extracted details from application content. */
@Builder(toBuilder = true)
public record ParsedAppContentDetails(
    UUID applyApplicationId,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    List<LinkedApplication> allLinkedApplications) {}
