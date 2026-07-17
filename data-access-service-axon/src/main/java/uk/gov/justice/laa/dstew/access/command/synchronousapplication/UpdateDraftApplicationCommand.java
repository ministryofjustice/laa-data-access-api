package uk.gov.justice.laa.dstew.access.command.synchronousapplication;

import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Command to update an existing draft application in place. */
public record UpdateDraftApplicationCommand(
    @TargetAggregateIdentifier UUID draftApplicationId, Map<String, Object> content) {}
