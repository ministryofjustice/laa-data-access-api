package uk.gov.justice.laa.dstew.access.domain;

import java.util.UUID;
import lombok.Builder;

/**
 * Domain record representing a linked application entry. Pure Java — no JPA, no Spring, no API
 * model imports.
 */
@Builder(toBuilder = true)
public record LinkedApplicationSummaryDomain(
    UUID applicationId, String laaReference, Boolean isLead, UUID leadApplicationId) {}
