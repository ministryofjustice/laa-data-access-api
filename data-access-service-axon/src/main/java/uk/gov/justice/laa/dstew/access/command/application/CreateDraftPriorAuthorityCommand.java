package uk.gov.justice.laa.dstew.access.command.application;

import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to start a new prior authority draft against an existing application. The draft body is
 * held as a free-form JSON payload; the aggregate only guards the true invariant that the parent
 * application exists.
 */
public record CreateDraftPriorAuthorityCommand(
    @TargetAggregateIdentifier UUID applyApplicationId, Map<String, Object> content) {}
