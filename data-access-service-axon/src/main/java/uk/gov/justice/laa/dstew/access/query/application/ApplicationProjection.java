package uk.gov.justice.laa.dstew.access.query.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadModel;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexSpecification;

/** Independently replayable projection of the current state of each Application. */
@Component
@ProcessingGroup("application-projection")
public class ApplicationProjection {

  private final ApplicationReadRepository applicationReadRepository;
  private final LinkedApplicationGroupReadRepository groupReadRepository;
  private final QueryUpdateEmitter queryUpdateEmitter;
  private final ApplicationDataStore applicationDataStore;
  private final ApplicationListIndexReadRepository listIndexRepository;

  /**
   * Constructs the projection with its read repositories and query update emitter.
   *
   * @param applicationReadRepository persistence interface for {@code application_current_state}
   * @param groupReadRepository persistence interface for {@code
   *     linked_application_group_current_state}; used by {@link FindAllApplicationsQuery} to
   *     batch-fetch group membership for the result page
   * @param queryUpdateEmitter used to push {@link ApplicationReadModel} updates to open
   *     subscription queries after {@code ApplicationCreatedEvent} is handled
   * @param listIndexRepository persistence interface for {@code application_list_index}; used by
   *     {@link FindAllApplicationsQuery} for database-side filtering and paging
   */
  public ApplicationProjection(
      ApplicationReadRepository applicationReadRepository,
      LinkedApplicationGroupReadRepository groupReadRepository,
      QueryUpdateEmitter queryUpdateEmitter,
      ApplicationDataStore applicationDataStore,
      ApplicationListIndexReadRepository listIndexRepository) {
    this.applicationReadRepository = applicationReadRepository;
    this.groupReadRepository = groupReadRepository;
    this.queryUpdateEmitter = queryUpdateEmitter;
    this.applicationDataStore = applicationDataStore;
    this.listIndexRepository = listIndexRepository;
  }

  /** Returns the current-state projection for the requested Application. */
  @QueryHandler
  public Optional<ApplicationReadModel> handle(FindApplicationByIdQuery query) {
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

  /**
   * Returns a paginated, filtered list of Application projections.
   *
   * <p>Filtering, sorting, counting, and paging are pushed entirely to the database via {@code
   * application_list_index}. After a page of index rows is returned, {@code application_data}
   * payloads are bulk-loaded for only those application IDs, avoiding N+1 lookups. Group membership
   * is similarly batch-fetched for the page and returned so the response mapper can populate {@code
   * linkedApplications} without additional queries.
   */
  @QueryHandler
  public FindAllApplicationsResult handle(FindAllApplicationsQuery query) {
    Sort sort = buildSort(query.sortBy(), query.orderBy());
    Pageable pageable = PageRequest.of(Math.max(0, query.page() - 1), query.pageSize(), sort);

    Page<ApplicationListIndexReadModel> indexPage =
        listIndexRepository.findAll(ApplicationListIndexSpecification.from(query), pageable);

    List<UUID> pageIds =
        indexPage.getContent().stream()
            .map(ApplicationListIndexReadModel::getApplicationId)
            .toList();

    // Single batch load of current-state rows for the page
    Map<UUID, ApplicationReadModel> stateById =
        applicationReadRepository.findAllById(pageIds).stream()
            .collect(Collectors.toMap(ApplicationReadModel::getApplicationId, Function.identity()));

    // Single batch load of application_data payloads for the page
    List<ApplicationDataId> dataIds =
        stateById.values().stream()
            .map(s -> new ApplicationDataId(s.getApplicationId(), s.getApplicationDataVersion()))
            .toList();
    Map<ApplicationDataId, ApplicationDataPayload> dataById = applicationDataStore.getAll(dataIds);

    // Assemble hydrated read models preserving the index ordering
    List<ApplicationReadModel> content =
        pageIds.stream()
            .map(stateById::get)
            .filter(Objects::nonNull)
            .map(
                state -> {
                  ApplicationDataId id =
                      new ApplicationDataId(
                          state.getApplicationId(), state.getApplicationDataVersion());
                  ApplicationDataPayload data = dataById.get(id);
                  return data == null ? null : hydrate(state, data);
                })
            .filter(Objects::nonNull)
            .toList();

    Map<UUID, LinkedApplicationGroupReadModel> groupsByLeadId = fetchGroups(content);

    return new FindAllApplicationsResult(
        content, groupsByLeadId, indexPage.getTotalElements(), query.page(), query.pageSize());
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

  private Sort buildSort(String sortBy, String orderBy) {
    String property = "LAST_UPDATED_DATE".equalsIgnoreCase(sortBy) ? "modifiedAt" : "submittedAt";
    Sort.Direction direction =
        "DESC".equalsIgnoreCase(orderBy) ? Sort.Direction.DESC : Sort.Direction.ASC;
    return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "applicationId"));
  }

  private Optional<ApplicationReadModel> hydrate(ApplicationReadModel application) {
    ApplicationDataId id =
        new ApplicationDataId(
            application.getApplicationId(), application.getApplicationDataVersion());
    ApplicationDataPayload data = applicationDataStore.getAll(List.of(id)).get(id);
    return data == null ? Optional.empty() : Optional.of(hydrate(application, data));
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

  /**
   * Batch-fetches group read models for the result page. For each application, the effective lead
   * ID is either its own ID (if it is a lead — {@code leadApplicationId} is null) or its {@code
   * leadApplicationId}. Groups are keyed by lead application ID for O(1) lookup in the mapper.
   */
  private Map<UUID, LinkedApplicationGroupReadModel> fetchGroups(
      List<ApplicationReadModel> applications) {
    List<UUID> leadIds =
        applications.stream()
            .map(
                app ->
                    app.getLeadApplicationId() != null
                        ? app.getLeadApplicationId()
                        : app.getApplicationId())
            .distinct()
            .toList();
    return groupReadRepository.findAllByLeadApplicationIdIn(leadIds).stream()
        .collect(
            Collectors.toMap(
                LinkedApplicationGroupReadModel::getLeadApplicationId, g -> g, (a, ignored) -> a));
  }
}
