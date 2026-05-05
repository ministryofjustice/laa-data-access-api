package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import java.util.List;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionOutcome;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;

/** Maps MakeDecisionRequest to MakeDecisionCommand. */
public class MakeDecisionCommandMapper {

  private final ObjectMapper objectMapper;

  /**
   * Constructs the mapper with the given ObjectMapper.
   *
   * @param objectMapper Jackson ObjectMapper for serialising the request
   */
  public MakeDecisionCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Maps a MakeDecisionRequest and applicationId to a MakeDecisionCommand.
   *
   * @param applicationId the UUID of the application
   * @param req the make decision request
   * @return the mapped command
   */
  public MakeDecisionCommand toCommand(UUID applicationId, MakeDecisionRequest req) {
    return MakeDecisionCommand.builder()
        .applicationId(applicationId)
        .applicationVersion(req.getApplicationVersion())
        .autoGranted(req.getAutoGranted())
        .overallDecision(OverallDecisionStatus.valueOf(req.getOverallDecision().name()))
        .proceedings(toDomainProceedings(req.getProceedings()))
        .certificate(req.getCertificate())
        .eventDescription(
            req.getEventHistory() != null ? req.getEventHistory().getEventDescription() : null)
        .serialisedRequest(serialise(req))
        .build();
  }

  private List<MakeDecisionProceedingCommand> toDomainProceedings(
      List<MakeDecisionProceedingRequest> proceedings) {
    return proceedings.stream()
        .map(
            p ->
                MakeDecisionProceedingCommand.builder()
                    .proceedingId(p.getProceedingId())
                    .meritsDecision(
                        MeritsDecisionOutcome.valueOf(p.getMeritsDecision().getDecision().name()))
                    .reason(p.getMeritsDecision().getReason())
                    .justification(p.getMeritsDecision().getJustification())
                    .build())
        .toList();
  }

  private String serialise(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialise request", e);
    }
  }
}
