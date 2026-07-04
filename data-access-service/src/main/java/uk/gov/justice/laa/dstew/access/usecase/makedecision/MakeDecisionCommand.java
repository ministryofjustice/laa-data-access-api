package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/** Input record for the makeDecision use case. No API model imports. */
@Builder(toBuilder = true)
public record MakeDecisionCommand(
    UUID applicationId,
    Long applicationVersion,
    String overallDecision,
    Boolean autoGranted,
    List<MakeDecisionProceedingCommand> proceedings,
    Map<String, Object> certificate,
    String serialisedRequest,
    String eventDescription) {}
