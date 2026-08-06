package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Internal command dispatched by {@link AssignmentInvariantService} after verifying that the
 * requesting caseworker is currently assigned to the WorkItem. Routes directly to
 * {@link uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate}.
 */
@Command(routingKey = "applicationId")
public record ExecuteApplicationDecisionCommand(
    @TargetEntityId UUID applicationId,
    long expectedApplicationVersion,
    String overallDecision,
    Boolean autoGranted,
    List<MakeDecisionProceeding> proceedings,
    Map<String, Object> certificate,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt,
    UUID caseworkerId) {

  public ExecuteApplicationDecisionCommand {
    proceedings = List.copyOf(proceedings);
    certificate = certificate == null ? null : Map.copyOf(certificate);
  }

  /** Promotes a {@link MakeApplicationDecisionCommand} to this internal command. */
  public static ExecuteApplicationDecisionCommand from(MakeApplicationDecisionCommand source) {
    return new ExecuteApplicationDecisionCommand(
        source.applicationId(),
        source.expectedApplicationVersion(),
        source.overallDecision(),
        source.autoGranted(),
        source.proceedings(),
        source.certificate(),
        source.serialisedRequest(),
        source.eventDescription(),
        source.occurredAt(),
        source.caseworkerId());
  }
}
