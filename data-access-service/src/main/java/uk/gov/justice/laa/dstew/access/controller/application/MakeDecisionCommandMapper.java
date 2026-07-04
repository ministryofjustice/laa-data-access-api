package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.List;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionCommand;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionProceedingCommand;

/** Maps {@link MakeDecisionRequest} to {@link MakeDecisionCommand}. */
public class MakeDecisionCommandMapper {

  private final ObjectMapper objectMapper;

  /**
   * Constructs the mapper with the provided Jackson ObjectMapper.
   *
   * @param objectMapper the Jackson ObjectMapper to use for serialisation
   */
  public MakeDecisionCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Converts a {@link MakeDecisionRequest} to a {@link MakeDecisionCommand}.
   *
   * @param applicationId the application UUID from the path variable
   * @param makeDecisionRequest the HTTP request model
   * @return the command record
   */
  public MakeDecisionCommand toMakeDecisionCommand(
      UUID applicationId, MakeDecisionRequest makeDecisionRequest) {
    return MakeDecisionCommand.builder()
        .applicationId(applicationId)
        .applicationVersion(makeDecisionRequest.getApplicationVersion())
        .overallDecision(
            makeDecisionRequest.getOverallDecision() != null
                ? makeDecisionRequest.getOverallDecision().name()
                : null)
        .autoGranted(makeDecisionRequest.getAutoGranted())
        .proceedings(toMakeDecisionProceedingCommands(makeDecisionRequest.getProceedings()))
        .certificate(makeDecisionRequest.getCertificate())
        .serialisedRequest(serialise(makeDecisionRequest))
        .eventDescription(
            makeDecisionRequest.getEventHistory() != null
                ? makeDecisionRequest.getEventHistory().getEventDescription()
                : null)
        .build();
  }

  private List<MakeDecisionProceedingCommand> toMakeDecisionProceedingCommands(
      List<MakeDecisionProceedingRequest> proceedings) {
    if (proceedings == null) {
      return List.of();
    }
    return proceedings.stream().map(this::toMakeDecisionProceedingCommand).toList();
  }

  private MakeDecisionProceedingCommand toMakeDecisionProceedingCommand(
      MakeDecisionProceedingRequest makeDecisionProceedingRequest) {
    return MakeDecisionProceedingCommand.builder()
        .proceedingId(makeDecisionProceedingRequest.getProceedingId())
        .decision(
            makeDecisionProceedingRequest.getMeritsDecision() != null
                    && makeDecisionProceedingRequest.getMeritsDecision().getDecision() != null
                ? makeDecisionProceedingRequest.getMeritsDecision().getDecision().name()
                : null)
        .reason(
            makeDecisionProceedingRequest.getMeritsDecision() != null
                ? makeDecisionProceedingRequest.getMeritsDecision().getReason()
                : null)
        .justification(
            makeDecisionProceedingRequest.getMeritsDecision() != null
                ? makeDecisionProceedingRequest.getMeritsDecision().getJustification()
                : null)
        .build();
  }

  @ExcludeFromGeneratedCodeCoverage
  private String serialise(MakeDecisionRequest makeDecisionRequest) {
    try {
      return objectMapper.writeValueAsString(makeDecisionRequest);
    } catch (JacksonException e) {
      throw new DomainEventPublishException(
          "Unable to serialise MakeDecisionRequest for domain event");
    }
  }
}
