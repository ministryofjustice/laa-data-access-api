package uk.gov.justice.laa.dstew.access.query.individual;

import java.util.List;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationAddress;

/** Optional application-level details used to enrich a client individual response. */
public record ApplicationClientDetails(
    String lastNameAtBirth,
    String previousApplicationId,
    String relationshipToInvolvedChildren,
    Boolean appliedPreviously,
    List<ApplicationAddress> correspondenceAddress) {}
