package uk.gov.justice.laa.dstew.access.command.synchronousapplication;

import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to update an existing prior authority draft in place. The aggregate guards that both the
 * parent application and the referenced prior authority draft exist.
 */
public record UpdateDraftPriorAuthorityCommand(
    @TargetAggregateIdentifier UUID applyApplicationId,
    UUID priorAuthorityId,
    Map<String, Object> content) {}
