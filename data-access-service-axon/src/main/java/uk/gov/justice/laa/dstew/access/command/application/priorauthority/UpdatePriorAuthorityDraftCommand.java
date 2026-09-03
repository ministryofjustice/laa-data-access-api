package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;

/** Command that updates an existing Prior Authority draft in progress. */
@Command(routingKey = "submissionId")
public record UpdatePriorAuthorityDraftCommand(
    @TargetEntityId UUID submissionId,
    PriorAuthorityContent content,
    String serialisedRequest,
    int schemaVersion,
    String schemaName,
    Instant occurredAt) {}
