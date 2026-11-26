package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;
import uk.gov.justice.laa.dstew.access.security.CallerCommand;

/**
 * Command that replaces the mutable body of an existing Application draft. The content is stored
 * without contract validation. {@code serialisedRequest} is the business-only request, with the
 * envelope {@code metadata} removed.
 */
@Command(routingKey = "applicationId")
public record UpdateApplicationDraftCommand(
    @TargetEntityId UUID applicationId,
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    String serialisedRequest,
    Instant occurredAt,
    List<PotentialDuplicate> potentialDuplicates,
    Map<String, Object> metadata,
    AuthenticatedCaller caller)
    implements CallerCommand {}
