package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Pointer event marking that a work item's caseworker assignment was cleared. Polymorphic across
 * the two work-item kinds: a null {@code priorAuthorityId} identifies an application-level item
 * (the root aggregate); a non-null {@code priorAuthorityId} identifies a prior authority member.
 */
public record CaseworkerUnassignedEvent(
    UUID applyApplicationId, UUID priorAuthorityId, Instant occurredAt) {}
