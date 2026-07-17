package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Lightweight command that proves an {@code ApplicationAggregate} stream exists.
 *
 * <p>Used by {@link ApplicationGroupEventRouter} to validate that referenced associated
 * applications exist before initialising a {@link LinkedApplicationGroupAggregate}. The handler on
 * {@code ApplicationAggregate} uses {@code CREATE_IF_MISSING}: if the targeted aggregate has no
 * event stream, Axon ghost-creates it and the {@code applicationId == null} guard throws {@link
 * uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException}, which propagates back
 * through the subscribing processor to the HTTP caller as a 404.
 */
public record ValidateApplicationExistsCommand(@TargetAggregateIdentifier UUID applicationId) {}
