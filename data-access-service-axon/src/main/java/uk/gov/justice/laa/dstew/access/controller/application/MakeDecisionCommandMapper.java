package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;

/** Maps the make-decision HTTP contract to an Axon command. */
@Component
public class MakeDecisionCommandMapper {

  private final ObjectMapper objectMapper;

  public MakeDecisionCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps a request for the supplied Application identifier. */
  public MakeApplicationDecisionCommand toCommand(UUID applicationId, MakeDecisionRequest request) {
    return new MakeApplicationDecisionCommand(
        applicationId,
        request.getApplicationVersion(),
        request.getOverallDecision().name(),
        request.getAutoGranted(),
        proceedings(request.getProceedings()),
        request.getCertificate(),
        serialise(request),
        request.getEventHistory() == null ? null : request.getEventHistory().getEventDescription(),
        Instant.now());
  }

  private List<MakeDecisionProceeding> proceedings(
      List<MakeDecisionProceedingRequest> proceedings) {
    if (proceedings == null) {
      return List.of();
    }
    return proceedings.stream()
        .map(
            proceeding ->
                new MakeDecisionProceeding(
                    proceeding.getProceedingId(),
                    proceeding.getMeritsDecision() == null
                            || proceeding.getMeritsDecision().getDecision() == null
                        ? null
                        : proceeding.getMeritsDecision().getDecision().name(),
                    proceeding.getMeritsDecision() == null
                        ? null
                        : proceeding.getMeritsDecision().getReason(),
                    proceeding.getMeritsDecision() == null
                        ? null
                        : proceeding.getMeritsDecision().getJustification()))
        .toList();
  }

  private String serialise(MakeDecisionRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise MakeDecisionRequest", exception);
    }
  }
}
