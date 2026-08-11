package uk.gov.justice.laa.dstew.access.applicationcontent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Record representing extracted details from application content. */
@Builder
public record ParsedAppContentDetails(
    ApplicationClient client,
    ApplicationProvider provider,
    List<Opponent> opponents,
    UUID applyApplicationId,
    String categoryOfLaw,
    String matterType,
    Instant submittedAt,
    Boolean usedDelegatedFunctions,
    List<Proceeding> proceedings,
    List<LinkedApplication> allLinkedApplications) {}
