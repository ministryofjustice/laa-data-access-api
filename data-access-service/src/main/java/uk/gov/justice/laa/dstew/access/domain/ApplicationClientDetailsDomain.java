package uk.gov.justice.laa.dstew.access.domain;

import java.util.List;
import java.util.Map;
import lombok.Builder;

/** Domain record carrying extended CLIENT_DETAILS fields sourced from ApplicationContent. */
@Builder(toBuilder = true)
public record ApplicationClientDetailsDomain(
    String lastNameAtBirth,
    String previousApplicationId,
    String relationshipToInvolvedChildren,
    String correspondenceAddressType,
    Boolean appliedPreviously,
    List<Map<String, Object>> correspondenceAddress) {}
