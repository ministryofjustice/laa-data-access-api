package uk.gov.justice.laa.dstew.access.usecase.makedecision;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.domain.enums.DecisionStatus;

/** Input record for the makeDecision use case. */
@Builder(toBuilder = true)
public record MakeDecisionCommand(
    UUID applicationId,
    Long applicationVersion,
    DecisionStatus overallDecision,
    Boolean autoGranted,
    List<MakeDecisionProceedingCommand> proceedings,
    Map<String, Object> certificate,
    String serialisedRequest,
    String eventDescription) {}
