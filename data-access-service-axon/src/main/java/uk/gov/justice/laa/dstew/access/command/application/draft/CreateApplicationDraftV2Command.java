package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/**
 * Command that starts an Application draft from the v2 API. The content is stored without contract
 * validation; validation is deferred to submission. {@code serialisedRequest} is the business-only
 * request, with the envelope {@code metadata} removed.
 */
@Command(routingKey = "applicationId")
public record CreateApplicationDraftV2Command(
    @TargetEntityId UUID applicationId,
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    String serialisedRequest,
    int schemaVersion,
    Instant occurredAt,
    List<PotentialDuplicate> potentialDuplicates,
    Map<String, Object> metadata) {}
