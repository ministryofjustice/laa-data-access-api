package uk.gov.justice.laa.dstew.access.query.genesis;

import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/**
 * Writes a pointer to every event for each genesis application it names, so history can read an
 * application's events across aggregates without holding state. Every event type has a handler
 * here; {@code GenesisScopeArchitectureTest} enforces that.
 */
@Component
@Namespace(GenesisStreamProjection.PROCESSING_GROUP)
public class GenesisStreamProjection {

  public static final String PROCESSING_GROUP = "genesis-stream";

  /** The source name for events held in this service's event store. */
  public static final String LOCAL_SOURCE = "data-access-api";

  private final GenesisStreamRepository repository;

  public GenesisStreamProjection(GenesisStreamRepository repository) {
    this.repository = repository;
  }

  @EventHandler
  void on(ApplicationCreatedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(ApplicationDecisionMadeEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(ApplicationDocumentUploadedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(ApplicationDraftStartedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(ApplicationReadyForManualAssessmentEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(ApplicationUpdatedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(LinkedApplicationGroupCreatedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(LinkedApplicationGroupDissolvedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(LinkedApplicationGroupLeadChangedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(MemberAddedToGroupEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(MemberRemovedFromGroupEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(NoteCreatedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(PriorAuthorityDecisionMadeEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(PriorAuthorityDocumentDeletedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(PriorAuthorityDocumentTypeUpdatedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(PriorAuthorityDocumentUploadedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(PriorAuthorityDraftStartedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(PriorAuthorityDraftUpdatedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(PriorAuthoritySubmittedEvent event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(WorkItemAssigned event, EventMessage message) {
    record(event, message);
  }

  @EventHandler
  void on(WorkItemUnassigned event, EventMessage message) {
    record(event, message);
  }

  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }

  /** Writes one pointer per genesis ID, skipping any already written by an earlier delivery. */
  void record(GenesisScoped event, EventMessage message) {
    for (var genesisId : event.genesisIds()) {
      if (repository.existsByGenesisIdAndSourceAndEventIdentifier(
          genesisId, LOCAL_SOURCE, message.identifier())) {
        continue;
      }
      repository.save(
          GenesisStreamEntry.builder()
              .genesisId(genesisId)
              .source(LOCAL_SOURCE)
              .eventIdentifier(message.identifier())
              .eventName(message.type().name())
              .occurredAt(event.occurredAt())
              .build());
    }
  }
}
