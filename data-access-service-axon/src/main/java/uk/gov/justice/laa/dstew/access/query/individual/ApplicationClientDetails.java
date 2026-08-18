package uk.gov.justice.laa.dstew.access.query.individual;

import java.util.List;
import java.util.Map;

/** Optional application-level details used to enrich a client individual response. */
public record ApplicationClientDetails(
    String lastNameAtBirth,
    String previousApplicationId,
    String relationshipToInvolvedChildren,
    String correspondenceAddressType,
    Boolean appliedPreviously,
    List<Map<String, Object>> correspondenceAddress) {}
