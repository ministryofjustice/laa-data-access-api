package uk.gov.justice.laa.dstew.access.query.application;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

/** Independently replayable projection of the current state of each Application. */
@Component
@ProcessingGroup("application-projection")
public class ApplicationProjection {

  private final ApplicationReadRepository applicationReadRepository;
  private final LinkedApplicationGroupReadRepository groupReadRepository;
  private final QueryUpdateEmitter queryUpdateEmitter;

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
      QueryUpdateEmitter queryUpdateEmitter) {
    this.applicationReadRepository = applicationReadRepository;
    this.groupReadRepository = groupReadRepository;
    this.queryUpdateEmitter = queryUpdateEmitter;
  }

  /** Returns the current-state projection for the requested Application. */
  @QueryHandler
  public java.util.Optional<ApplicationReadModel> handle(FindApplicationByIdQuery query) {
    return applicationReadRepository.findById(query.applicationId());
  }

  /**
   * Returns a paginated, filtered list of Application projections.
   *
   * <p>Filters on {@code status}, {@code laaReference}, and {@code matterType} are applied via JPA
   * {@link Specification}. Client name / date-of-birth filters are accepted for API compatibility
   * but not yet applied — those fields are stored in the {@code individuals} JSON column and
   * require either a denormalisation migration or a JSONB path query to support.
   *
   * <p>Group membership is batch-fetched for the result page and returned in the result so the
   * response mapper can populate {@code linkedApplications} without additional queries.
   */
  @QueryHandler
  public FindAllApplicationsResult handle(FindAllApplicationsQuery query) {
    int page = Math.max(0, query.page() - 1); // convert 1-based to 0-based
    int pageSize = query.pageSize() > 0 ? query.pageSize() : 20;

    Sort sort = buildSort(query.sortBy(), query.orderBy());
    Page<ApplicationReadModel> resultPage =
        applicationReadRepository.findAll(buildSpec(query), PageRequest.of(page, pageSize, sort));

    List<ApplicationReadModel> content = resultPage.getContent();
    Map<UUID, LinkedApplicationGroupReadModel> groupsByLeadId = fetchGroups(content);

    return new FindAllApplicationsResult(
        content, groupsByLeadId, resultPage.getTotalElements(), query.page(), pageSize);
  }

  /** Creates the current-state row from an Application's creation event. */
  @EventHandler
  public void on(ApplicationCreatedEvent event) {
    ApplicationReadModel saved =
        applicationReadRepository.save(
            ApplicationReadModel.builder()
                .applicationId(event.applicationId())
                .status(event.status())
                .laaReference(event.laaReference())
                .applicationContent(event.applicationContent())
                .individuals(event.individuals())
                .schemaVersion(event.schemaVersion())
                .applicationType(event.applicationType())
                .applyApplicationId(event.applyApplicationId())
                .submittedAt(event.submittedAt())
                .officeCode(event.officeCode())
                .usedDelegatedFunctions(event.usedDelegatedFunctions())
                .categoryOfLaw(event.categoryOfLaw() == null ? null : event.categoryOfLaw().name())
                .matterType(event.matterType() == null ? null : event.matterType().name())
                .proceedings(event.proceedings())
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

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    applicationReadRepository.deleteAllInBatch();
  }

  private Specification<ApplicationReadModel> buildSpec(FindAllApplicationsQuery query) {
    return (root, cq, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (query.status() != null) {
        predicates.add(cb.equal(root.get("status"), query.status()));
      }
      if (query.laaReference() != null) {
        predicates.add(cb.equal(root.get("laaReference"), query.laaReference()));
      }
      if (query.matterType() != null) {
        predicates.add(cb.equal(root.get("matterType"), query.matterType()));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private Sort buildSort(String sortBy, String orderBy) {
    Sort.Direction direction =
        "DESC".equalsIgnoreCase(orderBy) ? Sort.Direction.DESC : Sort.Direction.ASC;
    String column = "LAST_UPDATED_DATE".equalsIgnoreCase(sortBy) ? "modifiedAt" : "submittedAt";
    return Sort.by(direction, column);
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
