package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.axonframework.serialization.Revision;

/** Records the Application that owns an Apply Application identifier. */
@Revision("1")
public record ApplyApplicationIdClaimedEvent(
    UUID applyApplicationId,
    UUID applicationId,
    ApplicationFinalisationDetails applicationFinalisationDetails,
    UUID leadApplicationId) {}
