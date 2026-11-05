package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;
import uk.gov.justice.laa.dstew.access.security.CallerCommand;

/** Command that submits an in-progress Prior Authority draft, sealing its content. */
@Command(routingKey = "priorAuthorityId")
public record SubmitPriorAuthorityDraftCommand(
    @TargetEntityId UUID priorAuthorityId, Instant occurredAt, AuthenticatedCaller caller)
    implements CallerCommand {

  /** Temporary source-compatible constructor for non-HTTP command fixtures. */
  @Deprecated(forRemoval = true)
  public SubmitPriorAuthorityDraftCommand(UUID priorAuthorityId, Instant occurredAt) {
    this(priorAuthorityId, occurredAt, null);
  }
}
