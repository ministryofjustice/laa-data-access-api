package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * Persisted event emitted on every prior-authority draft-body mutation.
 *
 * <p>A thin, PII-free pointer carrying no draft content. Its sole purpose is to route every draft
 * write through the aggregate's per-stream optimistic-concurrency gate, so concurrent writers
 * (draft edit vs document upload, or edit vs edit) serialise and retry-converge instead of silently
 * last-write-wins dropping a change.
 */
public record PriorAuthorityDraftUpdatedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID parentApplicationId,
    Instant occurredAt) {}
