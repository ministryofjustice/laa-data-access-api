package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/** Event-sourced consistency boundary for an Application and its owned child state. */
@EventSourced(tagKey = "ApplicationAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationAggregate {

  private UUID applicationId;
  private final ApplicationState state = new ApplicationState();

  public UUID getApplicationId() {
    return applicationId;
  }

  public String getOfficeCode() {
    return state.officeCode;
  }

  public ApplicationState getState() {
    return state;
  }

  public boolean isGranted() {
    return "GRANTED".equals(state.overallDecision);
  }

  @EventSourcingHandler
  void on(ApplicationCreatedEvent event) {
    ApplicationEvolve.apply(state, event);
    this.applicationId = state.applicationId;
  }

  @EventSourcingHandler
  void on(ApplicationDraftStartedEvent event) {
    ApplicationEvolve.apply(state, event);
    this.applicationId = state.applicationId;
  }

  @EventSourcingHandler
  void on(ApplicationDecisionMadeEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(WorkItemAssigned event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(WorkItemUnassigned event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(NoteCreatedEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationReadyForManualAssessmentEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationUpdatedEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationDocumentUploadedEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EntityCreator
  protected ApplicationAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
