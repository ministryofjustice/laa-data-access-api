package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;

/** Maps the make-decision HTTP contract to a prior-authority decision command. */
@Component
public class MakePriorAuthorityDecisionCommandMapper {

  private final ObjectMapper objectMapper;

  public MakePriorAuthorityDecisionCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps a request for the supplied PriorAuthority identifier. */
  public MakePriorAuthorityDecisionCommand toCommand(
      UUID priorAuthorityId, MakeDecisionRequest request) {
    return new MakePriorAuthorityDecisionCommand(
        priorAuthorityId,
        request.getOverallDecision().name(),
        serialise(request),
        decisionJustification(request),
        Instant.now());
  }

  private String decisionJustification(MakeDecisionRequest request) {
    if (request.getEventHistory() != null
        && request.getEventHistory().getEventDescription() != null) {
      String value = request.getEventHistory().getEventDescription().trim();
      if (!value.isEmpty()) {
        return value;
      }
    }
    List<MakeDecisionProceedingRequest> proceedings = request.getProceedings();
    if (proceedings == null) {
      return null;
    }
    return proceedings.stream()
        .map(MakeDecisionProceedingRequest::getMeritsDecision)
        .filter(merits -> merits != null && merits.getJustification() != null)
        .map(merits -> merits.getJustification().trim())
        .filter(justification -> !justification.isEmpty())
        .findFirst()
        .orElse(null);
  }

  private String serialise(MakeDecisionRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise MakeDecisionRequest", exception);
    }
  }
}
