package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Command that submits an in-progress Application draft, sealing its content. Carries no body: the
 * content already accumulated via the create/update draft commands is validated in full and
 * promoted to the immutable {@code application_data} store.
 */
@Command(routingKey = "applicationId")
public record SubmitApplicationDraftCommand(
    @TargetEntityId UUID applicationId, Instant occurredAt) {}
