package uk.gov.justice.laa.dstew.access.domain;

import java.util.UUID;
import lombok.Builder;

/** Domain record representing a linked application entry. */
@Builder(toBuilder = true)
public record LinkedApplicationSummaryDomain(
    UUID applicationId, String laaReference, Boolean isLead, UUID leadApplicationId) {}
