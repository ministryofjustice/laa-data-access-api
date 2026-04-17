package uk.gov.justice.laa.dstew.access.domain;

import java.util.UUID;

/** Domain record representing a linked application pair. */
public record LinkedApplication(UUID leadApplicationId, UUID associatedApplicationId) {}
