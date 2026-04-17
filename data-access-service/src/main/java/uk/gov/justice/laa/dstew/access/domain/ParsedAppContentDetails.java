package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domain record representing extracted details from application content. */
public record ParsedAppContentDetails(
    UUID applyApplicationId,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    List<LinkedApplication> allLinkedApplications) {}
