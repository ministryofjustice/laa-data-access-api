package uk.gov.justice.laa.dstew.access.replay;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GapAwareTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.eventstreaming.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModelEvolve;

/** Reconstructs an Application read model through Axon's native, tag-filtered event-store API. */
@Service
public class ApplicationEventStoreReplayService {

  static final String APPLICATION_TAG_KEY = "ApplicationAggregate";

  private static final Logger log =
      LoggerFactory.getLogger(ApplicationEventStoreReplayService.class);

  private interface EventApplier {
    void apply(ApplicationReadModel model, Message message);
  }

  private static final Map<String, EventApplier> EVENT_APPLIERS =
      Map.of(
          "ApplicationCreatedEvent",
          (model, message) ->
              ApplicationReadModelEvolve.apply(
                  model, message.payloadAs(ApplicationCreatedEvent.class)),
          "ApplicationDecisionMadeEvent",
          (model, message) ->
              ApplicationReadModelEvolve.apply(
                  model, message.payloadAs(ApplicationDecisionMadeEvent.class)),
          "ApplicationAssignedToCaseworkerEvent",
          (model, message) ->
              ApplicationReadModelEvolve.apply(
                  model, message.payloadAs(ApplicationAssignedToCaseworkerEvent.class)),
          "ApplicationUnassignedFromCaseworkerEvent",
          (model, message) ->
              ApplicationReadModelEvolve.apply(
                  model, message.payloadAs(ApplicationUnassignedFromCaseworkerEvent.class)),
          "NoteCreatedEvent",
          (model, message) ->
              ApplicationReadModelEvolve.apply(model, message.payloadAs(NoteCreatedEvent.class)),
          "ApplicationLinkedEvent",
          (model, message) ->
              ApplicationReadModelEvolve.apply(
                  model, message.payloadAs(ApplicationLinkedEvent.class)),
          "LinkedApplicationGroupRequested",
          (model, message) ->
              ApplicationReadModelEvolve.apply(
                  model, message.payloadAs(LinkedApplicationGroupRequested.class)));

  private final EventStore eventStore;
  private final ApplicationDataStore applicationDataStore;

  /**
   * Construct the service which replays events from the Axon event store.
   *
   * @param eventStore the Axon EventStore to read events from
   * @param applicationDataStore the persistent application data store used when hydrating the read
   *     model
   */
  public ApplicationEventStoreReplayService(
      EventStore eventStore, ApplicationDataStore applicationDataStore) {
    this.eventStore = eventStore;
    this.applicationDataStore = applicationDataStore;
  }

  /**
   * Replays the currently available historical entries matching the Application tag.
   *
   * <p>Axon event streams are live and infinite. A GET must not wait for future entries, so this
   * method drains entries immediately available after opening the stream and then closes it.
   */
  public Optional<ApplicationReadModel> replay(UUID applicationId) {
    ApplicationReadModel model = new ApplicationReadModel();
    TrackingToken token =
        new GapAwareTrackingToken(0, null); // Start from the beginning of the stream
    MessageStream<EventMessage> stream =
        eventStore.open(
            StreamingCondition.startingFrom(token)
                .withCriteria(
                    EventCriteria.havingTags(
                        Tag.of(APPLICATION_TAG_KEY, applicationId.toString()))),
            null);
    boolean receivedEvent = false;

    try {
      while (stream.hasNextAvailable()) {
        var possibleEntry = stream.next();
        if (possibleEntry.isEmpty()) {
          continue;
        }
        receivedEvent = true;
        apply(model, possibleEntry.get().message());
      }
      stream
          .error()
          .ifPresent(
              error -> {
                throw new IllegalStateException(
                    "Unable to replay event stream for application " + applicationId, error);
              });
    } catch (IllegalStateException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Unable to replay event stream for application " + applicationId, exception);
    } finally {
      stream.close();
    }

    if (!receivedEvent) {
      return Optional.empty();
    }
    ApplicationDataPayload data =
        applicationDataStore.get(applicationId, model.getApplicationDataVersion());
    return Optional.of(hydrate(model, data));
  }

  private void apply(ApplicationReadModel model, Message message) {
    String simpleName =
        message.type().qualifiedName().localName(); // Get the simple class name of the event

    EventApplier applier = EVENT_APPLIERS.get(simpleName);
    if (applier == null) {
      // Unknown/irrelevant event for the Application read model; ignore.
      log.debug("Skipping unknown event type during replay: {}", simpleName);
      return;
    }

    try {
      applier.apply(model, message);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to apply event " + simpleName + " during replay", ex);
    }
  }

  private ApplicationReadModel hydrate(ApplicationReadModel model, ApplicationDataPayload data) {
    model.setLaaReference(data.laaReference());
    model.setApplicationContent(data.applicationContent());
    model.setIndividuals(data.individuals());
    model.setSubmittedAt(data.submittedAt());
    model.setOfficeCode(data.officeCode());
    model.setUsedDelegatedFunctions(data.usedDelegatedFunctions());
    model.setCategoryOfLaw(data.categoryOfLaw() == null ? null : data.categoryOfLaw().name());
    model.setMatterType(data.matterType() == null ? null : data.matterType().name());
    model.setProceedings(data.proceedings());
    model.setDecisionStatus(data.overallDecision());
    model.setAutoGranted(data.autoGranted());
    model.setMeritsDecisions(data.meritsDecisions());
    model.setCertificate(data.certificate());
    return model;
  }
}
