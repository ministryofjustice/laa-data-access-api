package uk.gov.justice.laa.dstew.access.usecase.shared.parser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/** Record representing extracted details from application content. */
@Builder
public record ParsedAppContentDetails(
    UUID applyApplicationId,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    List<Proceeding> proceedings,
    List<LinkedApplication> allLinkedApplications) {}
