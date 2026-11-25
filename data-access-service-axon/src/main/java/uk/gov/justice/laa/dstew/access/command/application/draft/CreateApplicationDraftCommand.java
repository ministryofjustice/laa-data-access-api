package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/**
 * Command that starts (or, on a fresh aggregate, first saves) an Application draft. The content is
 * schema-validated eagerly, before this command reaches the aggregate, then carried as erasable
 * content on {@link ApplicationDraftStartedEvent}.
 */
@Command(routingKey = "applicationId")
public record CreateApplicationDraftCommand(
    @TargetEntityId UUID applicationId,
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    String requestFingerprint,
    int schemaVersion,
    String schemaName,
    Instant occurredAt,
    List<PotentialDuplicate> potentialDuplicates) {}
