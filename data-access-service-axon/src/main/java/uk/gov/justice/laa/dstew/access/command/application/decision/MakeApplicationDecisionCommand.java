package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Requests a decision against the current version of an Application. */
@Command(routingKey = "applicationId")
public record MakeApplicationDecisionCommand(
    @TargetEntityId UUID applicationId,
    long expectedApplicationVersion,
    String overallDecision,
    Boolean autoGranted,
    List<MakeDecisionProceeding> proceedings,
    Map<String, Object> certificate,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {

  public MakeApplicationDecisionCommand {
    proceedings = List.copyOf(proceedings);
    certificate = certificate == null ? null : Map.copyOf(certificate);
  }
}
