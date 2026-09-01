package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Command that submits an in-progress Prior Authority draft, sealing its content. */
@Command(routingKey = "submissionId")
public record SubmitPriorAuthorityDraftCommand(
    @TargetEntityId UUID submissionId, Instant occurredAt) {}
