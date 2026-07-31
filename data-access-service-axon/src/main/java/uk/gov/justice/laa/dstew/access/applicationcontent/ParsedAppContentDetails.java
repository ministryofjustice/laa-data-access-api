package uk.gov.justice.laa.dstew.access.applicationcontent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Record representing extracted details from application content. */
@Builder
public record ParsedAppContentDetails(
    ApplicationContent applicationContent,
    UUID applyApplicationId,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    List<Proceeding> proceedings,
    List<LinkedApplication> allLinkedApplications) {}
