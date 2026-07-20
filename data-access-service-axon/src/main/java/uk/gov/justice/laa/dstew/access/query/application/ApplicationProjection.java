package uk.gov.justice.laa.dstew.access.query.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

/** Independently replayable projection of the current state of each Application. */
@Component
@ProcessingGroup("application-projection")
public class ApplicationProjection {

  private final ApplicationReadRepository applicationReadRepository;
  private final LinkedApplicationGroupReadRepository groupReadRepository;
  private final QueryUpdateEmitter queryUpdateEmitter;
  private final ApplicationDataStore applicationDataStore;

  /**
   * Constructs the projection with its read repositories and query update emitter.
   *
   * @param applicationReadRepository persistence interface for {@code application_current_state}
   * @param groupReadRepository persistence interface for {@code
   *     linked_application_group_current_state}; used by {@link FindAllApplicationsQuery} to
   *     batch-fetch group membership for the result page
   * @param queryUpdateEmitter used to push {@link ApplicationReadModel} updates to open
   *     subscription queries after {@code ApplicationCreatedEvent} is handled
   */
  public ApplicationProjection(
      ApplicationReadRepository applicationReadRepository,
      LinkedApplicationGroupReadRepository groupReadRepository,
      QueryUpdateEmitter queryUpdateEmitter,
      ApplicationDataStore applicationDataStore) {
    this.applicationReadRepository = applicationReadRepository;
    this.groupReadRepository = groupReadRepository;
    this.queryUpdateEmitter = queryUpdateEmitter;
    this.applicationDataStore = applicationDataStore;
  }

  /** Returns the current-state projection for the requested Application. */
  @QueryHandler
  public java.util.Optional<ApplicationReadModel> handle(FindApplicationByIdQuery query) {
    return applicationReadRepository.findById(query.applicationId()).flatMap(this::hydrate);
  }

  /**
   * Returns a paginated, filtered list of Application projections.
   *
   * <p>The thin current-state rows are batch-hydrated from their referenced immutable data versions
   * before filtering, sorting, and pagination. This keeps PII out of the disposable projection.
   *
   * <p>Group membership is batch-fetched for the result page and returned in the result so the
   * response mapper can populate {@code linkedApplications} without additional queries.
   */
  @QueryHandler
  public FindAllApplicationsResult handle(FindAllApplicationsQuery query) {
    int page = Math.max(0, query.page() - 1);
    int pageSize = query.pageSize() > 0 ? query.pageSize() : 20;

    List<ApplicationReadModel> filtered =
        hydrate(applicationReadRepository.findAll()).stream()
            .filter(application -> matches(application, query))
            .sorted(comparator(query.sortBy(), query.orderBy()))
            .toList();
    int fromIndex = Math.min(page * pageSize, filtered.size());
    int toIndex = Math.min(fromIndex + pageSize, filtered.size());
    List<ApplicationReadModel> content = filtered.subList(fromIndex, toIndex);
    Map<UUID, LinkedApplicationGroupReadModel> groupsByLeadId = fetchGroups(content);

    return new FindAllApplicationsResult(
        content, groupsByLeadId, filtered.size(), query.page(), pageSize);
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

  private boolean matches(ApplicationReadModel application, FindAllApplicationsQuery query) {
    return (query.status() == null || Objects.equals(application.getStatus(), query.status()))
        && (query.laaReference() == null
            || Objects.equals(application.getLaaReference(), query.laaReference()))
        && (query.matterType() == null
            || Objects.equals(application.getMatterType(), query.matterType()));
  }

  private Comparator<ApplicationReadModel> comparator(String sortBy, String orderBy) {
    Comparator<ApplicationReadModel> comparator =
        "LAST_UPDATED_DATE".equalsIgnoreCase(sortBy)
            ? Comparator.comparing(
                ApplicationReadModel::getModifiedAt,
                Comparator.nullsLast(Comparator.naturalOrder()))
            : Comparator.comparing(
                ApplicationReadModel::getSubmittedAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
    return "DESC".equalsIgnoreCase(orderBy) ? comparator.reversed() : comparator;
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
                LinkedApplicationGroupReadModel::getLeadApplicationId, g -> g, (a, b) -> a));
  }
}
