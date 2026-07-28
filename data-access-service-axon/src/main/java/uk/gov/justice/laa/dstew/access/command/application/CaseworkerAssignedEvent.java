package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Pointer event marking that a caseworker was assigned to a work item. Polymorphic across the two
 * work-item kinds: a null {@code priorAuthorityId} identifies an application-level item (the root
 * aggregate); a non-null {@code priorAuthorityId} identifies a prior authority member. Carries no
 * personal data.
 */
public record CaseworkerAssignedEvent(
    UUID applyApplicationId, UUID priorAuthorityId, UUID caseworkerId, Instant occurredAt) {}
