package uk.gov.justice.laa.dstew.access.query.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationNote;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;

/** Independently replayable projection of the current state of each Application. */
@Component
@ProcessingGroup("application-projection")
public class ApplicationProjection {

  private final ApplicationReadRepository applicationReadRepository;
  private final QueryUpdateEmitter queryUpdateEmitter;
  private final ApplicationDataStore applicationDataStore;

  /**
   * Constructs the projection with its read repositories and query update emitter.
   *
   * @param applicationReadRepository persistence interface for {@code application_current_state}
   * @param queryUpdateEmitter used to push {@link ApplicationReadModel} updates to open
   *     subscription queries after {@code ApplicationCreatedEvent} is handled
   */
  public ApplicationProjection(
      ApplicationReadRepository applicationReadRepository,
      QueryUpdateEmitter queryUpdateEmitter,
      ApplicationDataStore applicationDataStore) {
    this.applicationReadRepository = applicationReadRepository;
    this.queryUpdateEmitter = queryUpdateEmitter;
    this.applicationDataStore = applicationDataStore;
  }

  /** Returns the current-state projection for the requested Application. */
  @QueryHandler
  public java.util.Optional<ApplicationReadModel> handle(FindApplicationByIdQuery query) {
    return applicationReadRepository.findById(query.applicationId()).flatMap(this::hydrate);
  }

  /**
   * Returns all notes for the requested Application, ordered by creation time ascending, or {@link
   * Optional#empty()} if no application with the given ID exists.
   */
  @QueryHandler
  public Optional<ApplicationNotesResult> handle(FindNotesForApplicationQuery query) {
    return applicationReadRepository
        .findById(query.applicationId())
        .map(
            application -> {
              ApplicationDataPayload data =
                  applicationDataStore.get(
                      application.getApplicationId(), application.getApplicationDataVersion());
              return new ApplicationNotesResult(
                  data == null ? List.<ApplicationNote>of() : data.notes());
            });
  }

  /** Creates the current-state row from an Application's creation event. */
  @EventHandler
  public void on(ApplicationCreatedEvent event) {
    ApplicationReadModel saved =
        applicationReadRepository.save(
            ApplicationReadModel.builder()
                .applicationId(event.applicationId())
                .status(event.status())
                .applicationDataVersion(event.applicationDataVersion())
                .applicationVersion(0L)
                .schemaVersion(event.schemaVersion())
                .applicationType(event.applicationType())
                .applyApplicationId(event.applyApplicationId())
                .createdAt(event.occurredAt())
                .modifiedAt(event.occurredAt())
                .leadApplicationId(event.leadApplicationId())
                .build());
    queryUpdateEmitter.emit(
        FindApplicationByIdQuery.class,
        query -> query.applicationId().equals(event.applicationId()),
        saved);
  }

  /** Updates the linked-group membership after the Application is created. */
  @EventHandler
  public void on(ApplicationLinkedEvent event) {
    applicationReadRepository
        .findById(event.applicationId())
        .ifPresent(
            application -> {
              application.setLeadApplicationId(event.leadApplicationId());
              application.setModifiedAt(event.occurredAt());
              applicationReadRepository.save(application);
            });
  }

  /** Advances the current-state row to the immutable data version containing the decision. */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event) {
    applicationReadRepository
        .findById(event.applicationId())
        .ifPresent(
            application -> {
              application.setApplicationDataVersion(event.applicationDataVersion());
              application.setApplicationVersion(event.applicationVersion());
              application.setModifiedAt(event.occurredAt());
              ApplicationReadModel saved = applicationReadRepository.save(application);
              queryUpdateEmitter.emit(
                  FindApplicationByIdQuery.class,
                  query -> query.applicationId().equals(event.applicationId()),
                  saved);
            });
  }

  /** Updates the assigned caseworker and referenced application-data version. */
  @EventHandler
  public void on(ApplicationAssignedToCaseworkerEvent event) {
    applicationReadRepository
        .findById(event.applicationId())
        .ifPresent(
            application -> {
              application.setCaseworkerId(event.caseworkerId());
              application.setApplicationVersion(event.applicationVersion());
              application.setApplicationDataVersion(event.applicationDataVersion());
              application.setModifiedAt(event.occurredAt());
              applicationReadRepository.save(application);
            });
  }

  /** Clears the assigned caseworker and updates the referenced application-data version. */
  @EventHandler
  public void on(ApplicationUnassignedFromCaseworkerEvent event) {
    applicationReadRepository
        .findById(event.applicationId())
        .ifPresent(
            application -> {
              application.setCaseworkerId(null);
              application.setApplicationVersion(event.applicationVersion());
              application.setApplicationDataVersion(event.applicationDataVersion());
              application.setModifiedAt(event.occurredAt());
              applicationReadRepository.save(application);
            });
  }

  /** Advances the referenced application-data version when a note is created. */
  @EventHandler
  public void on(NoteCreatedEvent event) {
    applicationReadRepository
        .findById(event.applicationId())
        .ifPresent(
            application -> {
              application.setApplicationDataVersion(event.applicationDataVersion());
              application.setModifiedAt(event.occurredAt());
              applicationReadRepository.save(application);
            });
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    applicationReadRepository.deleteAllInBatch();
  }

  private Optional<ApplicationReadModel> hydrate(ApplicationReadModel application) {
    ApplicationDataId id =
        new ApplicationDataId(
            application.getApplicationId(), application.getApplicationDataVersion());
    ApplicationDataPayload data = applicationDataStore.getAll(List.of(id)).get(id);
    return data == null ? Optional.empty() : Optional.of(hydrate(application, data));
  }

  private List<ApplicationReadModel> hydrate(List<ApplicationReadModel> applications) {
    List<ApplicationDataId> ids =
        applications.stream()
            .map(
                application ->
                    new ApplicationDataId(
                        application.getApplicationId(), application.getApplicationDataVersion()))
            .toList();
    Map<ApplicationDataId, ApplicationDataPayload> dataById = applicationDataStore.getAll(ids);
    return applications.stream()
        .map(
            application -> {
              ApplicationDataId id =
                  new ApplicationDataId(
                      application.getApplicationId(), application.getApplicationDataVersion());
              ApplicationDataPayload data = dataById.get(id);
              return data == null ? null : hydrate(application, data);
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private ApplicationReadModel hydrate(
      ApplicationReadModel application, ApplicationDataPayload data) {
    application.setLaaReference(data.laaReference());
    application.setApplicationContent(data.applicationContent());
    application.setIndividuals(data.individuals());
    application.setSubmittedAt(data.submittedAt());
    application.setOfficeCode(data.officeCode());
    application.setUsedDelegatedFunctions(data.usedDelegatedFunctions());
    application.setCategoryOfLaw(data.categoryOfLaw() == null ? null : data.categoryOfLaw().name());
    application.setMatterType(data.matterType() == null ? null : data.matterType().name());
    application.setProceedings(data.proceedings());
    application.setDecisionStatus(data.overallDecision());
    application.setAutoGranted(data.autoGranted());
    application.setMeritsDecisions(data.meritsDecisions());
    application.setCertificate(data.certificate());
    return application;
  }
}
