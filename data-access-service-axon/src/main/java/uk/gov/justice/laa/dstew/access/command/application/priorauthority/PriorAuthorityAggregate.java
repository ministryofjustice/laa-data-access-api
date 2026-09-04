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
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

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

  private UUID priorAuthorityId;
  private final PriorAuthorityState state = new PriorAuthorityState();

  @CommandHandler
  void handle(
      CreatePriorAuthorityCommand command,
      PriorAuthorityDataStore dataStore,
      EventAppender eventAppender) {
    String fingerprint;
    if (priorAuthorityId == null) {
      PriorAuthorityDataPayload payload =
          new PriorAuthorityDataPayload(
              command.priorAuthorityId(),
              command.applicationId(),
              command.content(),
              command.serialisedRequest(),
              command.occurredAt());
      fingerprint =
          dataStore.append(
              command.priorAuthorityId(),
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

  @CommandHandler
  void handle(
      CreatePriorAuthorityDraftCommand command,
      PriorAuthorityDraftStore draftStore,
      EventAppender eventAppender) {
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            command.priorAuthorityId(),
            command.applicationId(),
            command.content(),
            command.serialisedRequest(),
            command.occurredAt());
    String fingerprint =
        draftStore.upsert(
            command.priorAuthorityId(),
            command.applicationId(),
            payload,
            command.serialisedRequest(),
            command.occurredAt());
    eventAppender.append(
        PriorAuthorityDecider.decideStartDraft(command, fingerprint, command.applicationId()));
  }

  @CommandHandler
  void handle(UpdatePriorAuthorityDraftCommand command, PriorAuthorityDraftStore draftStore) {
    draftStore
        .find(command.priorAuthorityId())
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Prior Authority %s not found".formatted(command.priorAuthorityId())));
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            command.priorAuthorityId(),
            state.applicationId,
            command.content(),
            command.serialisedRequest(),
            command.occurredAt());
    draftStore.upsert(
        command.priorAuthorityId(),
        state.applicationId,
        payload,
        command.serialisedRequest(),
        command.occurredAt());
  }

  @CommandHandler
  void handle(
      SubmitPriorAuthorityDraftCommand command,
      PriorAuthorityDraftStore draftStore,
      PriorAuthorityDataStore dataStore,
      JsonSchemaValidator jsonSchemaValidator,
      EventAppender eventAppender) {
    PriorAuthorityDataPayload payload =
        draftStore
            .find(command.priorAuthorityId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority %s not found".formatted(command.priorAuthorityId())));
    jsonSchemaValidator.validate(payload.content(), "PriorAuthority.json", state.schemaVersion);
    dataStore.append(
        command.priorAuthorityId(),
        0L,
        state.applicationId,
        payload,
        payload.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(PriorAuthorityDecider.decideSubmit(command, state));
    draftStore.delete(command.priorAuthorityId());
  }

  @EventSourcingHandler
  void on(PriorAuthorityCreatedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EventSourcingHandler
  void on(PriorAuthorityDraftStartedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EventSourcingHandler
  void on(PriorAuthoritySubmittedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EntityCreator
  protected PriorAuthorityAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
