package uk.gov.justice.laa.dstew.access.domain;

import java.util.UUID;
import lombok.Builder;

/** Domain record representing a linked application pair. */
@Builder(toBuilder = true)
public record LinkedApplication(UUID leadApplicationId, UUID associatedApplicationId) {}
