package uk.gov.justice.laa.dstew.access.query.application.listindex;

import java.util.List;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ResetHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;

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
@ProcessingGroup("application-list-index-projection")
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
  public void on(ApplicationCreatedEvent event, EventMessage<?> message) {
    ApplicationDataPayload data =
        applicationDataStore.get(event.applicationId(), event.applicationDataVersion());

    ApplicationIndividual client = primaryClient(data);

    listIndexRepository.save(
        ApplicationListIndexReadModel.builder()
            .applicationId(event.applicationId())
            .status(event.status())
            .laaReference(data.laaReference())
            .caseworkerId(null)
            .matterType(data.matterType() == null ? null : data.matterType().name())
            .isAutoGranted(null)
            .submittedAt(data.submittedAt())
            .leadApplicationId(event.leadApplicationId())
            .clientFirstName(client != null ? client.firstName() : null)
            .clientLastName(client != null ? client.lastName() : null)
            .clientDateOfBirth(client != null ? client.dateOfBirth() : null)
            .streamVersion(0L)
            .projectionPosition(message.getIdentifier().hashCode())
            .build());
  }

  /** Updates the {@code lead_application_id} when an application is linked to a group. */
  @EventHandler
  public void on(ApplicationLinkedEvent event, EventMessage<?> message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setLeadApplicationId(event.leadApplicationId());
              row.setProjectionPosition(message.getIdentifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /**
   * Updates {@code status}, {@code is_auto_granted}, and {@code stream_version} when a decision is
   * made.
   */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event, EventMessage<?> message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setStatus(
                  event.overallDecision() != null ? event.overallDecision() : row.getStatus());
              row.setIsAutoGranted(event.autoGranted());
              row.setStreamVersion(event.applicationVersion());
              row.setProjectionPosition(message.getIdentifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /** Updates {@code caseworker_id} and {@code stream_version} when a caseworker is assigned. */
  @EventHandler
  public void on(ApplicationAssignedToCaseworkerEvent event, EventMessage<?> message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setCaseworkerId(event.caseworkerId());
              row.setStreamVersion(event.applicationVersion());
              row.setProjectionPosition(message.getIdentifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /** Clears {@code caseworker_id} and updates {@code stream_version} on unassignment. */
  @EventHandler
  public void on(ApplicationUnassignedFromCaseworkerEvent event, EventMessage<?> message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setCaseworkerId(null);
              row.setStreamVersion(event.applicationVersion());
              row.setProjectionPosition(message.getIdentifier().hashCode());
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
  public void on(NoteCreatedEvent event, EventMessage<?> message) {
    listIndexRepository
        .findById(event.applicationId())
        .ifPresent(
            row -> {
              row.setProjectionPosition(message.getIdentifier().hashCode());
              listIndexRepository.save(row);
            });
  }

  /** Clears the disposable index table before replay. */
  @ResetHandler
  public void reset() {
    listIndexRepository.deleteAllInBatch();
  }

  private ApplicationIndividual primaryClient(ApplicationDataPayload data) {
    List<ApplicationIndividual> individuals = data.individuals();
    if (individuals == null) {
      return null;
    }
    return individuals.stream().filter(i -> "CLIENT".equals(i.type())).findFirst().orElse(null);
  }
}
