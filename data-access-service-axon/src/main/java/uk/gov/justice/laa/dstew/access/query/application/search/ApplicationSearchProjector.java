package uk.gov.justice.laa.dstew.access.query.application.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.eventhandling.SequenceNumber;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationSearchView;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsQuery;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

/** Search projection used for FindAllApplicationsQuery filtering and pagination. */
@Component
@ProcessingGroup("application-search-projection")
public class ApplicationSearchProjector {

  private final ApplicationSearchRepository repository;
  private final ApplicationLinkSearchRepository linkRepository;
  private final LinkedApplicationGroupReadRepository groupReadRepository;
  private final ApplicationDataStore applicationDataStore;

  public ApplicationSearchProjector(
      ApplicationSearchRepository repository,
      ApplicationLinkSearchRepository linkRepository,
      LinkedApplicationGroupReadRepository groupReadRepository,
      ApplicationDataStore applicationDataStore) {
    this.repository = repository;
    this.linkRepository = linkRepository;
    this.groupReadRepository = groupReadRepository;
    this.applicationDataStore = applicationDataStore;
  }

  @EventHandler
  public void on(ApplicationCreatedEvent event, @SequenceNumber Long sequenceNumber) {
    ApplicationSearchView existing = repository.findById(event.applicationId()).orElse(null);
    if (existing != null
        && existing.getStreamVersion() != null
        && existing.getStreamVersion() >= sequenceNumber) {
      return;
    }

    ApplicationSearchView view = existing != null ? existing : new ApplicationSearchView();
    view.setApplicationId(event.applicationId());
    view.setStatus(event.status());
    view.setSchemaVersion(event.schemaVersion());
    view.setLeadApplicationId(event.leadApplicationId());
    view.setIsLead(
        event.leadApplicationId() == null
            || event.leadApplicationId().equals(event.applicationId()));
    view.setCreatedAt(event.occurredAt());
    view.setModifiedAt(event.occurredAt());
    view.setStreamVersion(sequenceNumber);
    hydrate(view, event.applicationDataVersion());
    repository.save(view);
  }

  @EventHandler
  public void on(ApplicationAssignedToCaseworkerEvent event, @SequenceNumber Long sequenceNumber) {
    updateSearchView(
        event.applicationId(),
        sequenceNumber,
        view -> {
          view.setCaseworkerId(
              event.caseworkerId() == null ? null : event.caseworkerId().toString());
          view.setModifiedAt(event.occurredAt());
          hydrate(view, event.applicationDataVersion());
        });
  }

  @EventHandler
  public void on(
      ApplicationUnassignedFromCaseworkerEvent event, @SequenceNumber Long sequenceNumber) {
    updateSearchView(
        event.applicationId(),
        sequenceNumber,
        view -> {
          view.setCaseworkerId(null);
          view.setModifiedAt(event.occurredAt());
          hydrate(view, event.applicationDataVersion());
        });
  }

  @EventHandler
  public void on(ApplicationDecisionMadeEvent event, @SequenceNumber Long sequenceNumber) {
    updateSearchView(
        event.applicationId(),
        sequenceNumber,
        view -> {
          view.setStatus(event.overallDecision());
          view.setIsAutoGranted(event.autoGranted());
          view.setModifiedAt(event.occurredAt());
          hydrate(view, event.applicationDataVersion());
        });
  }

  @EventHandler
  public void on(ApplicationLinkedEvent event, @SequenceNumber Long sequenceNumber) {
    updateSearchView(
        event.applicationId(),
        sequenceNumber,
        view -> {
          view.setLeadApplicationId(event.leadApplicationId());
          view.setIsLead(false);
          view.setModifiedAt(event.occurredAt());
        });
    createLinkSearchView(event.leadApplicationId(), event.applicationId(), sequenceNumber);
  }

  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event, @SequenceNumber Long sequenceNumber) {
    for (UUID memberId : event.memberApplicationIds()) {
      updateSearchView(
          memberId,
          sequenceNumber,
          view -> {
            view.setLeadApplicationId(event.leadApplicationId());
            view.setIsLead(memberId.equals(event.leadApplicationId()));
            view.setModifiedAt(event.occurredAt());
          });
      createLinkSearchView(event.leadApplicationId(), memberId, sequenceNumber);
    }
  }

  @EventHandler
  public void on(MemberAddedToGroupEvent event, @SequenceNumber Long sequenceNumber) {
    groupReadRepository
        .findById(event.groupId())
        .map(LinkedApplicationGroupReadModel::getLeadApplicationId)
        .ifPresent(
            leadId -> {
              updateSearchView(
                  event.memberId(),
                  sequenceNumber,
                  view -> {
                    view.setLeadApplicationId(leadId);
                    view.setIsLead(false);
                    view.setModifiedAt(event.occurredAt());
                  });
              createLinkSearchView(leadId, event.memberId(), sequenceNumber);
            });
  }

  @QueryHandler
  public FindAllApplicationsResult handle(FindAllApplicationsQuery query) {
    int pageNum = Math.max(0, query.page() - 1);
    int pageSize = query.pageSize() > 0 ? query.pageSize() : 20;

    Pageable pageable =
        PageRequest.of(pageNum, pageSize, buildSort(query.sortBy(), query.orderBy()));

    Page<ApplicationSearchView> page =
        repository.findAll(
            ApplicationSearchSpecification.withFilters(
                query.status(),
                query.laaReference(),
                query.caseworkerId(),
                query.matterType(),
                query.isAutoGranted(),
                query.clientFirstName(),
                query.clientLastName()),
            pageable);

    List<ApplicationReadModel> content = page.getContent().stream().map(this::toReadModel).toList();
    Map<UUID, LinkedApplicationGroupReadModel> groupsByLeadId =
        fetchGroupsFromLinkView(page.getContent());

    return new FindAllApplicationsResult(
        content, groupsByLeadId, page.getTotalElements(), query.page(), pageSize);
  }

  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
    linkRepository.deleteAllInBatch();
  }

  private void updateSearchView(
      UUID applicationId, Long sequenceNumber, Consumer<ApplicationSearchView> updater) {
    repository
        .findById(applicationId)
        .ifPresent(
            view -> {
              if (view.getStreamVersion() != null && view.getStreamVersion() >= sequenceNumber) {
                return;
              }
              updater.accept(view);
              view.setStreamVersion(sequenceNumber);
              repository.save(view);
            });
  }

  private void createLinkSearchView(UUID leadId, UUID memberId, Long streamVersion) {
    ApplicationLinkSearchView.ApplicationLinkId id =
        new ApplicationLinkSearchView.ApplicationLinkId(leadId, memberId);
    if (linkRepository.existsById(id)) {
      return;
    }
    linkRepository.save(
        ApplicationLinkSearchView.builder().id(id).streamVersion(streamVersion).build());
  }

  private void hydrate(ApplicationSearchView view, long dataVersion) {
    ApplicationDataPayload data = applicationDataStore.get(view.getApplicationId(), dataVersion);
    view.setLaaReference(data.laaReference());
    view.setSubmittedAt(data.submittedAt());
    view.setMatterType(data.matterType() == null ? null : data.matterType().name());
    view.setCategoryOfLaw(data.categoryOfLaw() == null ? null : data.categoryOfLaw().name());
    view.setIsAutoGranted(data.autoGranted());
    primaryClient(data)
        .ifPresent(
            client -> {
              view.setClientFirstName(client.firstName());
              view.setClientLastName(client.lastName());
              view.setClientDateOfBirth(client.dateOfBirth());
            });
  }

  private java.util.Optional<ApplicationIndividual> primaryClient(ApplicationDataPayload data) {
    if (data.individuals() == null) {
      return java.util.Optional.empty();
    }
    return data.individuals().stream()
        .filter(individual -> "CLIENT".equals(individual.type()))
        .findFirst();
  }

  private Map<UUID, LinkedApplicationGroupReadModel> fetchGroupsFromLinkView(
      List<ApplicationSearchView> views) {
    List<UUID> pageIds = views.stream().map(ApplicationSearchView::getApplicationId).toList();
    List<ApplicationLinkSearchView> linksByMember =
        linkRepository.findAllByApplicationIdIn(pageIds);

    List<UUID> leadIds =
        new ArrayList<>(
            linksByMember.stream()
                .map(ApplicationLinkSearchView::getLeadApplicationId)
                .distinct()
                .toList());
    views.stream()
        .filter(view -> Boolean.TRUE.equals(view.getIsLead()))
        .map(ApplicationSearchView::getApplicationId)
        .forEach(
            id -> {
              if (!leadIds.contains(id)) {
                leadIds.add(id);
              }
            });

    if (leadIds.isEmpty()) {
      return Map.of();
    }

    List<ApplicationLinkSearchView> linksByLead =
        linkRepository.findAllByLeadApplicationIdIn(leadIds);
    return linksByLead.stream()
        .collect(
            Collectors.groupingBy(
                ApplicationLinkSearchView::getLeadApplicationId,
                Collectors.mapping(
                    ApplicationLinkSearchView::getApplicationId, Collectors.toList())))
        .entrySet()
        .stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry ->
                    LinkedApplicationGroupReadModel.builder()
                        .groupId(entry.getKey())
                        .leadApplicationId(entry.getKey())
                        .memberIds(List.copyOf(entry.getValue()))
                        .build()));
  }

  private Sort buildSort(String sortBy, String orderBy) {
    String sortField = "LAST_UPDATED_DATE".equalsIgnoreCase(sortBy) ? "modifiedAt" : "submittedAt";
    Sort.Direction direction =
        "DESC".equalsIgnoreCase(orderBy) ? Sort.Direction.DESC : Sort.Direction.ASC;
    return Sort.by(
        new Sort.Order(direction, sortField), new Sort.Order(Sort.Direction.ASC, "applicationId"));
  }

  private ApplicationReadModel toReadModel(ApplicationSearchView view) {
    ApplicationReadModel readModel = new ApplicationReadModel();
    readModel.setApplicationId(view.getApplicationId());
    readModel.setStatus(view.getStatus());
    readModel.setLaaReference(view.getLaaReference());
    readModel.setMatterType(view.getMatterType());
    readModel.setCategoryOfLaw(view.getCategoryOfLaw());
    readModel.setSubmittedAt(view.getSubmittedAt());
    readModel.setModifiedAt(view.getModifiedAt());
    readModel.setCreatedAt(view.getCreatedAt());
    readModel.setLeadApplicationId(view.getLeadApplicationId());
    readModel.setCaseworkerId(
        view.getCaseworkerId() == null ? null : UUID.fromString(view.getCaseworkerId()));
    readModel.setAutoGranted(view.getIsAutoGranted());

    if (view.getClientFirstName() != null
        || view.getClientLastName() != null
        || view.getClientDateOfBirth() != null) {
      readModel.setIndividuals(
          List.of(
              new ApplicationIndividual(
                  null,
                  view.getClientFirstName(),
                  view.getClientLastName(),
                  view.getClientDateOfBirth(),
                  null,
                  "CLIENT")));
    }

    return readModel;
  }
}
