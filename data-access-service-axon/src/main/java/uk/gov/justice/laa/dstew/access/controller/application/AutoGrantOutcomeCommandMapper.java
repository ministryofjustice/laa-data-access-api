package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.AutograntedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;

/** Maps the outcome-specific auto-grant contract to existing domain commands. */
@Component
public class AutoGrantOutcomeCommandMapper {

  private final ObjectMapper objectMapper;

  public AutoGrantOutcomeCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps either terminal outcome for the supplied Application. */
  public Object toCommand(UUID applicationId, AutoGrantOutcomeRequest request) {
    validateDiscriminator(request);
    if (request instanceof ManualOutcomeRequest manual) {
      return new MarkApplicationReadyCommand(
          applicationId, manual.getApplicationVersion(), serialise(request), Instant.now());
    }
    if (request instanceof AutograntedOutcomeRequest autogranted) {
      return new MakeApplicationDecisionCommand(
          applicationId,
          autogranted.getApplicationVersion(),
          "GRANTED",
          true,
          proceedings(autogranted),
          autogranted.getCertificate(),
          serialise(request),
          autogranted.getEventHistory().getEventDescription(),
          Instant.now(),
          true);
    }
    throw new IllegalArgumentException("Unsupported auto-grant outcome request");
  }

  private void validateDiscriminator(AutoGrantOutcomeRequest request) {
    boolean manual =
        request instanceof ManualOutcomeRequest && request.getOutcome() == AutoGrantOutcome.MANUAL;
    boolean autogranted =
        request instanceof AutograntedOutcomeRequest
            && request.getOutcome() == AutoGrantOutcome.AUTOGRANTED;
    if (!manual && !autogranted) {
      throw new IllegalArgumentException("Auto-grant outcome does not match its payload");
    }
  }

  private List<MakeDecisionProceeding> proceedings(AutograntedOutcomeRequest request) {
    return request.getProceedings().stream()
        .map(
            proceeding ->
                new MakeDecisionProceeding(
                    proceeding.getProceedingId(),
                    proceeding.getMeritsDecision().getDecision().name(),
                    proceeding.getMeritsDecision().getReason(),
                    proceeding.getMeritsDecision().getJustification()))
        .toList();
  }

  private String serialise(AutoGrantOutcomeRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise AutoGrantOutcomeRequest", exception);
    }
  }
}
