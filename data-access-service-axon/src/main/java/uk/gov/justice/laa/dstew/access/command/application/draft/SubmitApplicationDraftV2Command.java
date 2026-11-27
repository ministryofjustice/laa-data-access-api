package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Command that submits a v2 Application draft. The stored content is validated against the
 * application contract before it is promoted to the immutable {@code application_data} store.
 */
@Command(routingKey = "applicationId")
public record SubmitApplicationDraftV2Command(
    @TargetEntityId UUID applicationId, Instant occurredAt) {}
