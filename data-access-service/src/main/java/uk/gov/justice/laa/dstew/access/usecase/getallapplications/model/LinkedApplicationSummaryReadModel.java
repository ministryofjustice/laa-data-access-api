package uk.gov.justice.laa.dstew.access.usecase.getallapplications.model;

import java.util.UUID;
import lombok.Builder;

/** Read model representing a linked application entry. */
@Builder(toBuilder = true)
public record LinkedApplicationSummaryReadModel(
    UUID applicationId, String laaReference, Boolean isLead, UUID leadApplicationId) {}
