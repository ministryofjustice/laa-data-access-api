package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Command that replaces the content of an in-progress Application draft. */
@Command(routingKey = "applicationId")
public record UpdateApplicationDraftCommand(
    @TargetEntityId UUID applicationId,
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    String serialisedRequest,
    Instant occurredAt) {}
