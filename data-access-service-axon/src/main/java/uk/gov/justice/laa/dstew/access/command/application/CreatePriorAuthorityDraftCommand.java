package uk.gov.justice.laa.dstew.access.command.application;

import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to start a new prior authority draft against an application. Prior authority is a
 * post-submission concern: the aggregate guards the true invariant that the target application is
 * already {@code SUBMITTED}. The draft body is held as a free-form JSON payload for now.
 */
public record CreatePriorAuthorityDraftCommand(
    @TargetAggregateIdentifier UUID applyApplicationId, Map<String, Object> content) {}
