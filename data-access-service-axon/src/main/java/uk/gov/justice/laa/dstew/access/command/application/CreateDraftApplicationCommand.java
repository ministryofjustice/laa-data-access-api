package uk.gov.justice.laa.dstew.access.command.application;

import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to start a new draft application. The draft body is held as a free-form JSON payload; the
 * draft shares its identity with the application it will eventually be submitted as.
 */
public record CreateDraftApplicationCommand(
    @TargetAggregateIdentifier UUID draftApplicationId, Map<String, Object> content) {}
