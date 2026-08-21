package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

/**
 * Event-sourced consistency boundary for a PriorAuthority submission.
 *
 * <p>On the first command for this aggregate ID, persists version 0 of the sensitive data and emits
 * {@link PriorAuthorityCreatedEvent}. On an identical retry (same serialised request), it returns
 * with no events. On a conflicting retry (different payload), throws {@link
 * uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException}.
 */
@EventSourced(tagKey = "PriorAuthorityAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PriorAuthorityAggregate {

  private UUID submissionId;
  private final PriorAuthorityState state = new PriorAuthorityState();

  @CommandHandler
  void handle(
      CreatePriorAuthorityCommand command,
      PriorAuthorityDataStore dataStore,
      EventAppender eventAppender) {
    String fingerprint;
    if (submissionId == null) {
      PriorAuthorityDataPayload payload =
          new PriorAuthorityDataPayload(
              command.submissionId(),
              command.applicationId(),
              command.content(),
              command.serialisedRequest(),
              command.occurredAt());
      fingerprint =
          dataStore.append(
              command.submissionId(),
              0L,
              command.applicationId(),
              payload,
              command.serialisedRequest(),
              command.occurredAt());
    } else {
      fingerprint = PayloadFingerprint.compute(command.serialisedRequest());
    }
    PriorAuthorityDecider.decideCreate(state, command, fingerprint)
        .ifPresent(e -> eventAppender.append(e));
  }

  @EventSourcingHandler
  void on(PriorAuthorityCreatedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.submissionId = state.submissionId;
  }

  @EntityCreator
  protected PriorAuthorityAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
