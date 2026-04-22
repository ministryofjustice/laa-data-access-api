package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;

/** Command record for the makeDecision use case. */
@Builder(toBuilder = true)
public record MakeDecisionCommand(
    UUID applicationId,
    Long applicationVersion,
    Boolean autoGranted,
    OverallDecisionStatus overallDecision,
    List<MakeDecisionProceedingCommand> proceedings,
    Map<String, Object> certificate,
    String eventDescription,
    String serialisedRequest) {}
