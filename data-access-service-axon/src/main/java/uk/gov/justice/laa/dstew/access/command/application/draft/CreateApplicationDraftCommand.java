package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Command that starts (or, on a fresh aggregate, first saves) an Application draft. The content is
 * schema-validated eagerly, before this command reaches the aggregate, then written directly to the
 * mutable draft store.
 */
@Command(routingKey = "applicationId")
public record CreateApplicationDraftCommand(
    @TargetEntityId UUID applicationId,
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    String serialisedRequest,
    int schemaVersion,
    String schemaName,
    Instant occurredAt) {}
