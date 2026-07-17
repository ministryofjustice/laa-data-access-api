package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Targets the lead {@code ApplicationAggregate} to prove it exists before forming the group.
 *
 * <p>Axon's {@code CREATE_IF_MISSING} policy on the target aggregate means that if the lead does
 * not exist, a fresh (empty) aggregate is constructed; the command handler's {@code applicationId
 * == null} guard detects this and throws {@link
 * uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException}.
 */
public record CreateLinkedApplicationGroupCommand(
    @TargetAggregateIdentifier UUID leadApplicationId,
    UUID associatedApplicationId,
    List<UUID> allMemberApplicationIds,
    String serialisedRequest,
    Instant occurredAt) {}
