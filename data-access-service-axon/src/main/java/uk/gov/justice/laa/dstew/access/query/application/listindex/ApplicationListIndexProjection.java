package uk.gov.justice.laa.dstew.access.query.application.listindex;

import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

/**
 * Independently replayable tracking projection that maintains {@code application_list_index}.
 *
 * <p>This projection writes PII filter columns ({@code client_first_name}, {@code
 * client_last_name}, {@code client_date_of_birth}) and other filter/sort fields into the index row
 * at event-handling time, reading them once from {@code application_data}. This means the list
 * query can push all filters and paging to the database and only bulk-load {@code application_data}
 * payloads for the page of results returned to the caller.
 *
 * <p>Each event handler reads from {@link ApplicationDataStore} at most once per event. There is no
 * N+1 risk: the query path reads {@code application_data} only for the page, not for every
 * candidate row.
 */
@Component
@Namespace("application-list-index-projection")
public class ApplicationListIndexProjection {

  private final ApplicationListIndexReadRepository listIndexRepository;
  private final ApplicationDataStore applicationDataStore;

  /** Constructs the projection with its repository and data store. */
  public ApplicationListIndexProjection(
      ApplicationListIndexReadRepository listIndexRepository,
      ApplicationDataStore applicationDataStore) {
    this.listIndexRepository = listIndexRepository;
    this.applicationDataStore = applicationDataStore;
  }

  /**
   * Inserts the initial index row from an Application's creation event.
   *
   * <p>Reads the referenced {@code application_data} version once to populate PII filter columns
   * and filter fields that are not carried on the thin event.
   */
  @EventHandler
  public void on(ApplicationCreatedEvent event, EventMessage message) {
    ApplicationDataPayload data =
        applicationDataStore.get(event.applicationId(), event.applicationDataVersion());

    ApplicationClient client = data.client();

    listIndexRepository.save(
        ApplicationListIndexReadModel.builder()
            .applicationId(event.applicationId())
            .status(event.status())
            .laaReference(data.laaReference())
            .caseworkerId(null)
            .matterType(data.matterType() == null ? null : data.matterType())
            .autoGranted(AutoGrantedState.PENDING)
            .submittedAt(data.submittedAt())
            .modifiedAt(event.occurredAt())
            .leadApplicationId(event.leadApplicationId())
            .clientFirstName(client != null ? client.getFirstName() : null)
            .clientLastName(client != null ? client.getLastName() : null)
            .clientDateOfBirth(client != null ? client.getDateOfBirth() : null)
            .streamVersion(0L)
            .projectionPosition(message.identifier().hashCode())
            .build());
  }

  /** Updates the {@code lead_application_id} when an application is linked to a group. */
  @EventHandler
  public void on(ApplicationLinkedEvent event, EventMessage message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setLeadApplicationId(event.leadApplicationId());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /**
   * Updates {@code status}, {@code is_auto_granted}, and {@code stream_version} when a decision is
   * made.
   */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event, EventMessage message) {
    ApplicationStatus applicationStatus;
    if (event.overallDecision() == null || event.overallDecision().isBlank()) {
      applicationStatus = null;

    } else {
      applicationStatus =
          DecisionValue.valueOf(event.overallDecision()).equals(DecisionValue.REFUSED)
              ? ApplicationStatus.APPLICATION_REFUSED
              : ApplicationStatus.APPLICATION_GRANTED;
    }
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setStatus(applicationStatus != null ? applicationStatus.name() : row.getStatus());
              row.setAutoGranted(event.autoGranted());
              row.setStreamVersion(event.applicationVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /** Records that automatic assessment completed with a manual-assessment outcome. */
  @EventHandler
  public void on(ApplicationReadyForManualAssessmentEvent event, EventMessage message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setAutoGranted(AutoGrantedState.MANUAL);
              row.setStreamVersion(event.applicationVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /** Refreshes filter and sort fields from the new immutable application-data version. */
  @EventHandler
  public void on(ApplicationUpdatedEvent event, EventMessage message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              ApplicationDataPayload data =
                  applicationDataStore.get(event.applicationId(), event.applicationDataVersion());
              ApplicationClient client = data.client();
              row.setStatus(event.status());
              row.setLaaReference(data.laaReference());
              row.setMatterType(data.matterType() == null ? null : data.matterType());
              row.setAutoGranted(data.autoGranted());
              row.setSubmittedAt(data.submittedAt());
              row.setClientFirstName(client != null ? client.getFirstName() : null);
              row.setClientLastName(client != null ? client.getLastName() : null);
              row.setClientDateOfBirth(client != null ? client.getDateOfBirth() : null);
              row.setStreamVersion(event.applicationVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /** Updates {@code caseworker_id} and {@code stream_version} when a caseworker is assigned. */
  @EventHandler
  public void on(ApplicationAssignedToCaseworkerEvent event, EventMessage message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setCaseworkerId(event.caseworkerId());
              row.setStreamVersion(event.applicationVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /** Clears {@code caseworker_id} and updates {@code stream_version} on unassignment. */
  @EventHandler
  public void on(ApplicationUnassignedFromCaseworkerEvent event, EventMessage message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setCaseworkerId(null);
              row.setStreamVersion(event.applicationVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /**
   * Mirrors generic direct application assignment without making this search index authoritative.
   */
  @EventHandler
  public void on(WorkItemAssigned event, EventMessage message) {
    if (event.workItemId().type() != WorkItemType.APPLICATION) {
      return;
    }
    listIndexRepository
        .findById(event.workItemId().id())
        .ifPresent(
            row -> {
              row.setCaseworkerId(event.caseworkerId());
              row.setStreamVersion(event.itemVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /**
   * Mirrors generic direct application unassignment without making this search index authoritative.
   */
  @EventHandler
  public void on(WorkItemUnassigned event, EventMessage message) {
    if (event.workItemId().type() != WorkItemType.APPLICATION) {
      return;
    }
    listIndexRepository
        .findById(event.workItemId().id())
        .ifPresent(
            row -> {
              row.setCaseworkerId(null);
              row.setStreamVersion(event.itemVersion());
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /**
   * Updates {@code projection_position} when a note is created.
   *
   * <p>Note creation does not change any filter or sort field on the index, so only the position
   * bookkeeping column is updated.
   */
  @EventHandler
  public void on(NoteCreatedEvent event, EventMessage message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setModifiedAt(event.occurredAt());
              row.setProjectionPosition(message.identifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /** Clears the disposable index table before replay. */
  @ResetHandler
  public void reset() {
    listIndexRepository.deleteAllInBatch();
  }
}
