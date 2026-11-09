package uk.gov.justice.laa.dstew.access.command.application.ready;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/** Records that the Application is ready for manual assessment ({@code autoGranted=MANUAL}). */
@Event(namespace = "access", name = "ApplicationReadyForManualAssessment")
public record ApplicationReadyForManualAssessmentEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationVersion,
    Instant occurredAt)
    implements GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
