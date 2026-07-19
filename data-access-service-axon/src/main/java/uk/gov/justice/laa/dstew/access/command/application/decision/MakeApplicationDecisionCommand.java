package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Requests a decision against the current version of an Application. */
public record MakeApplicationDecisionCommand(
    @TargetAggregateIdentifier UUID applicationId,
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
