package uk.gov.justice.laa.dstew.access.applicationcontent;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/** Record representing extracted details from application content. */
@Builder
public record ParsedAppContentDetails(
    ApplicationClient client,
    ApplicationProvider provider,
    List<Opponent> opponents,
    String categoryOfLaw,
    String matterType,
    Instant submittedAt,
    Boolean usedDelegatedFunctions,
    List<Proceeding> proceedings,
    List<LinkedApplication> allLinkedApplications) {}
