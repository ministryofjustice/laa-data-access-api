package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Records an automatic grant determined by the external auto-grant process. */
@Command(routingKey = "applicationId")
public record RecordAutoGrantedOutcomeCommand(
    @TargetEntityId UUID applicationId,
    Map<String, Object> certificate,
    String serialisedRequest,
    Instant occurredAt) {

  public RecordAutoGrantedOutcomeCommand {
    certificate = Map.copyOf(certificate);
  }
}
