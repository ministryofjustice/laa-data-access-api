package uk.gov.justice.laa.dstew.access.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/** Domain record representing a proceeding within application content. */
@Builder(toBuilder = true)
public record Proceeding(
    UUID id,
    String categoryOfLaw,
    String matterType,
    Boolean usedDelegatedFunctions,
    Boolean leadProceeding,
    String description,
    String meaning,
    LocalDate usedDelegatedFunctionsOn,
    String substantiveCostLimitation,
    String substantiveLevelOfServiceName,
    List<Map<String, Object>> scopeLimitations) {}
