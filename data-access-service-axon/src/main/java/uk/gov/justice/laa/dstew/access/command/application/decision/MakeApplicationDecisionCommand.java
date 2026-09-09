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
    UUID caseworkerId,
    long expectedApplicationVersion,
    String overallDecision,
    List<MakeDecisionProceeding> proceedings,
    Map<String, Object> certificate,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {

  /**
   * Legacy constructor retained while callers migrate to the assignment-authorised decision
   * contract. Step 3 will enforce a non-null ownership identity for manual decisions.
   */
  @Deprecated(forRemoval = true)
  public MakeApplicationDecisionCommand(
      UUID applicationId,
      long expectedApplicationVersion,
      String overallDecision,
      List<MakeDecisionProceeding> proceedings,
      Map<String, Object> certificate,
      String serialisedRequest,
      String eventDescription,
      Instant occurredAt) {
    this(
        applicationId,
        null,
        expectedApplicationVersion,
        overallDecision,
        proceedings,
        certificate,
        serialisedRequest,
        eventDescription,
        occurredAt);
  }

  /**
   * Legacy constructor retained while callers migrate away from the removed automatic-grant flag.
   * Normal decisions always represent manual assessment.
   */
  @Deprecated(forRemoval = true)
  public MakeApplicationDecisionCommand(
      UUID applicationId,
      long expectedApplicationVersion,
      String overallDecision,
      Boolean ignoredAutoGranted,
      List<MakeDecisionProceeding> proceedings,
      Map<String, Object> certificate,
      String serialisedRequest,
      String eventDescription,
      Instant occurredAt) {
    this(
        applicationId,
        null,
        expectedApplicationVersion,
        overallDecision,
        proceedings,
        certificate,
        serialisedRequest,
        eventDescription,
        occurredAt);
  }

  public MakeApplicationDecisionCommand {
    proceedings = List.copyOf(proceedings);
    certificate = certificate == null ? null : Map.copyOf(certificate);
  }
}
