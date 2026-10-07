package uk.gov.justice.laa.dstew.access.query.application;

import static uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus.APPLICATION_SUBMITTED;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.UploadDocument;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadModel;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexSpecification;

/** Independently replayable projection of the current state of each Application. */
@Component
@SequencingPolicy
@RequiredArgsConstructor
@Namespace("application-projection")
public class ApplicationProjection {

  private final ApplicationReadRepository applicationReadRepository;
  private final ApplicationDataStore applicationDataStore;
  private final ApplicationDraftStore draftStore;
  private final ApplicationReadQueryGateway applicationReadQueryGateway;
  private final ApplicationCurrentStateAccessPolicy currentStateAccessPolicy;
  private final ApplicationListIndexAccessPolicy listIndexAccessPolicy;
  private final ApplicationReadModelAssembler assembler;

  /** Returns the hydrated Application and its related data, or {@code null} if absent. */
  @QueryHandler
  public @Nullable ApplicationDetailResult handle(FindApplicationDetailQuery query) {
    return applicationReadQueryGateway
        .findApplication(
            (root, criteriaQuery, cb) -> cb.equal(root.get("applicationId"), query.applicationId()),
            currentStateAccessPolicy.restrictionFor(query.accessScope()))
        .flatMap(assembler::hydrate)
        .map(assembler::assembleDetail)
        .orElse(null);
  }

  /** Returns whether the projection exists, including drafts without submitted content. */
  @QueryHandler
  public boolean handle(ApplicationProjectionExistsQuery query) {
    return applicationReadRepository.existsById(query.applicationId());
  }

  /** Returns a live document with its filename hydrated, or {@code null} if either is absent. */
  @QueryHandler
  public @Nullable EvidenceDocument handle(FindApplicationDocumentQuery query) {
    return applicationReadRepository
        .findById(query.applicationId())
        .flatMap(
            application ->
                application.getUploadedDocuments().stream()
                    .filter(document -> document.documentId().equals(query.documentId()))
                    .filter(document -> !document.deleted())
                    .findFirst()
                    .flatMap(
                        document ->
                            documentFilename(application, document.documentId())
                                .map(fileName -> evidenceDocument(document, fileName))))
        .orElse(null);
  }

  /** Returns the current-state projection for the requested Application. */
  @QueryHandler
  public @Nullable ApplicationReadModel handle(FindApplicationByIdQuery query) {
    return applicationReadQueryGateway
        .findApplication(
            (root, criteriaQuery, cb) -> cb.equal(root.get("applicationId"), query.applicationId()),
            currentStateAccessPolicy.restrictionFor(query.accessScope()))
        .flatMap(assembler::hydrate)
        .orElse(null);
  }

  /**
   * Returns all notes for the requested Application, ordered by creation time ascending, or {@code
   * null} if no application with the given ID exists.
   */
  @QueryHandler
  public @Nullable ApplicationNotesResult handle(FindNotesForApplicationQuery query) {
    return applicationReadQueryGateway
        .findApplication(
            (root, criteriaQuery, cb) -> cb.equal(root.get("applicationId"), query.applicationId()),
            currentStateAccessPolicy.restrictionFor(query.accessScope()))
        .map(
            application -> {
              ApplicationDataPayload data =
                  applicationDataStore.get(
                      application.getApplicationId(), application.getApplicationDataVersion());
              return new ApplicationNotesResult(data == null ? List.of() : data.notes());
            })
        .orElse(null);
  }

  /**
   * Returns a paginated, filtered list of Application projections.
   *
   * <p>Filtering, sorting, counting, and paging are pushed entirely to the database via {@code
   * application_list_index}. After a page of index rows is returned, {@code application_data}
   * payloads are bulk-loaded for only those application IDs, avoiding N+1 lookups. Group membership
   * and linked prior authorities are similarly batch-fetched for the page and returned so the
   * response mapper can populate {@code linkedApplications} and prior-authority summaries without
   * additional queries.
   */
  @QueryHandler
  public FindAllApplicationsResult handle(FindAllApplicationsQuery query) {
    Sort sort = buildSort(query.sortBy(), query.orderBy());
    Pageable pageable = PageRequest.of(Math.max(0, query.page() - 1), query.pageSize(), sort);

    Page<ApplicationListIndexReadModel> indexPage =
        applicationReadQueryGateway.findApplicationIndexPage(
            ApplicationListIndexSpecification.from(query),
            pageable,
            listIndexAccessPolicy.restrictionFor(query.accessScope()));

    List<UUID> pageIds =
        indexPage.getContent().stream()
            .map(ApplicationListIndexReadModel::getApplicationId)
            .toList();

    Map<UUID, ApplicationReadModel> stateById =
        applicationReadQueryGateway
            .findApplications(pageIds, currentStateAccessPolicy.restrictionFor(query.accessScope()))
            .stream()
            .collect(Collectors.toMap(ApplicationReadModel::getApplicationId, Function.identity()));

    // Applications whose data payload is missing are dropped, preserving the index ordering.
    List<ApplicationReadModel> content =
        assembler.hydrate(pageIds.stream().map(stateById::get).filter(Objects::nonNull).toList());

    Map<UUID, LinkedApplicationGroupReadModel> groupsByGroupId = assembler.fetchGroups(content);

    return new FindAllApplicationsResult(
        content,
        groupsByGroupId,
        assembler.fetchLinkedLaaReferences(groupsByGroupId.values()),
        assembler.fetchPriorAuthorities(content),
        indexPage.getTotalElements(),
        query.page(),
        query.pageSize());
  }

  /** Returns old submitted Applications that still have no automatic-assessment outcome. */
  @QueryHandler
  public StalledAssessments handle(FindStalledAssessmentsQuery query) {
    return new StalledAssessments(
        assembler
            .hydrate(applicationReadRepository.findAllByStatus(APPLICATION_SUBMITTED.name()))
            .stream()
            .filter(application -> application.getAutoGranted() == AutoGrantedState.PENDING)
            .filter(application -> application.getSubmittedAt() != null)
            .filter(application -> application.getSubmittedAt().isBefore(query.submittedBefore()))
            .map(
                application ->
                    new StalledAssessment(
                        application.getApplicationId(),
                        application.getApplicationVersion(),
                        application.getSubmittedAt()))
            .toList());
  }

  /** Creates the current-state row from an Application's creation event. */
  @EventHandler
  public void on(ApplicationCreatedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    ApplicationDataPayload data =
        applicationDataStore.get(event.applicationId(), event.applicationDataVersion());
    ApplicationReadModel existing =
        applicationReadRepository.findById(event.applicationId()).orElse(null);
    ApplicationReadModel saved =
        applicationReadRepository.save(
            ApplicationReadModel.builder()
                .applicationId(event.applicationId())
                .status(event.status())
                .applicationDataVersion(event.applicationDataVersion())
                .applicationVersion(0L)
                .schemaVersion(event.schemaVersion())
                .createdAt(existing == null ? event.occurredAt() : existing.getCreatedAt())
                .submittedAt(event.occurredAt())
                .modifiedAt(event.occurredAt())
                .leadApplicationId(null)
                .linkedGroupId(null)
                .potentialDuplicates(event.potentialDuplicates())
                .officeCode(ApplicationReadModelAssembler.officeCode(data))
                .uploadedDocuments(existing == null ? List.of() : existing.getUploadedDocuments())
                .build());
    queryUpdateEmitter.emit(
        FindApplicationByIdQuery.class,
        query -> query.applicationId().equals(event.applicationId()),
        saved);
  }

  /** Updates application rows when a linked group is established explicitly. */
  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event) {
    event
        .memberApplicationIds()
        .forEach(
            memberApplicationId ->
                updateGroupMembership(
                    memberApplicationId,
                    event.groupId(),
                    memberApplicationId.equals(event.leadApplicationId())
                        ? null
                        : event.leadApplicationId(),
                    event.occurredAt()));
  }

  /** Updates the added member row when it joins an existing linked group explicitly. */
  @EventHandler
  public void on(MemberAddedToGroupEvent event) {
    updateGroupMembership(
        event.memberId(), event.groupId(), event.leadApplicationId(), event.occurredAt());
  }

  /** Updates the lead reference for every application in the changed group. */
  @EventHandler
  public void on(LinkedApplicationGroupLeadChangedEvent event) {
    List<ApplicationReadModel> members =
        applicationReadRepository.findAllByLinkedGroupId(event.groupId());
    members.forEach(
        application -> {
          application.setLeadApplicationId(
              application.getApplicationId().equals(event.newLeadApplicationId())
                  ? null
                  : event.newLeadApplicationId());
          application.setModifiedAt(event.occurredAt());
        });
    applicationReadRepository.saveAll(members);
  }

  /** Clears group membership from an application that leaves a group. */
  @EventHandler
  public void on(MemberRemovedFromGroupEvent event) {
    updateGroupMembership(event.memberId(), null, null, event.occurredAt());
  }

  /** Clears group membership from every application when its group is dissolved. */
  @EventHandler
  public void on(LinkedApplicationGroupDissolvedEvent event) {
    event
        .memberApplicationIds()
        .forEach(memberId -> updateGroupMembership(memberId, null, null, event.occurredAt()));
  }

  /** Advances the current-state row to the immutable data version containing the decision. */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    advanceCurrentStateWithStatus(
        event.applicationId(),
        event.applicationVersion(),
        event.applicationDataVersion(),
        event.overallDecision(),
        event.occurredAt(),
        queryUpdateEmitter);
  }

  /** Advances the current-state row to the immutable data version containing manual readiness. */
  @EventHandler
  public void on(
      ApplicationReadyForManualAssessmentEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    advanceCurrentState(
        event.applicationId(),
        event.applicationVersion(),
        event.applicationDataVersion(),
        event.occurredAt(),
        queryUpdateEmitter);
  }

  /** Advances current state to the updated immutable data and status. */
  @EventHandler
  public void on(ApplicationUpdatedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    applicationReadRepository
        .findById(event.applicationId())
        .ifPresent(
            application -> {
              application.setStatus(event.status());
              application.setApplicationVersion(event.applicationVersion());
              application.setApplicationDataVersion(event.applicationDataVersion());
              application.setModifiedAt(event.occurredAt());
              application.setOfficeCode(
                  ApplicationReadModelAssembler.officeCode(
                      applicationDataStore.get(
                          event.applicationId(), event.applicationDataVersion())));
              ApplicationReadModel saved = applicationReadRepository.save(application);
              queryUpdateEmitter.emit(
                  FindApplicationByIdQuery.class,
                  query -> query.applicationId().equals(event.applicationId()),
                  saved);
            });
  }

  /** Updates the assigned caseworker from an application work-list assignment. */
  @EventHandler
  public void on(WorkItemAssigned event) {
    if (event.workItemType() != WorkItemType.APPLICATION) {
      return;
    }
    applicationReadRepository
        .findById(event.workItemId())
        .ifPresent(
            application -> {
              application.setCaseworkerId(event.caseworkerId());
              application.setModifiedAt(event.occurredAt());
              applicationReadRepository.save(application);
            });
  }

  /** Clears the assigned caseworker from an application work-list unassignment. */
  @EventHandler
  public void on(WorkItemUnassigned event) {
    if (event.workItemType() != WorkItemType.APPLICATION) {
      return;
    }
    applicationReadRepository
        .findById(event.workItemId())
        .ifPresent(
            application -> {
              application.setCaseworkerId(null);
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

  /** Projects filename-free document metadata and its sensitive-data version together. */
  @EventHandler
  public void on(ApplicationDocumentUploadedEvent event) {
    ApplicationReadModel application =
        applicationReadRepository
            .findById(event.applicationId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Application not found for document upload: " + event.applicationId()));
    List<UploadDocument> documents = new ArrayList<>(application.getUploadedDocuments());
    if (documents.stream().anyMatch(document -> document.documentId().equals(event.documentId()))) {
      return;
    }
    documents.add(
        new UploadDocument(
            event.documentId(),
            event.documentType(),
            event.uploadedAt(),
            event.size(),
            event.contentType(),
            event.checksum(),
            event.sourceService(),
            false));
    application.setUploadedDocuments(List.copyOf(documents));
    if (event.applicationDataVersion() != null) {
      application.setApplicationDataVersion(event.applicationDataVersion());
    }
    application.setModifiedAt(event.uploadedAt());
    applicationReadRepository.save(application);
  }

  /** Creates the current-state row when an application draft is started. */
  @EventHandler
  public void on(ApplicationDraftStartedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    ApplicationReadModel application =
        applicationReadRepository
            .findById(event.applicationId())
            .orElseGet(
                () ->
                    ApplicationReadModel.builder()
                        .applicationId(event.applicationId())
                        .status(ApplicationStatus.APPLICATION_IN_PROGRESS.getValue())
                        .applicationDataVersion(0L)
                        .applicationVersion(0L)
                        .schemaVersion(event.schemaVersion())
                        .createdAt(event.occurredAt())
                        .modifiedAt(event.occurredAt())
                        .build());
    applicationReadRepository.save(application);
    queryUpdateEmitter.emit(
        ApplicationProjectionExistsQuery.class,
        query -> query.applicationId().equals(event.applicationId()),
        true);
  }

  private void updateGroupMembership(
      UUID applicationId, UUID groupId, UUID leadApplicationId, Instant occurredAt) {
    applicationReadRepository
        .findById(applicationId)
        .ifPresent(
            application -> {
              application.setLinkedGroupId(groupId);
              application.setLeadApplicationId(leadApplicationId);
              application.setModifiedAt(occurredAt);
              applicationReadRepository.save(application);
            });
  }

  private void advanceCurrentState(
      UUID applicationId,
      long applicationVersion,
      long applicationDataVersion,
      Instant occurredAt,
      QueryUpdateEmitter queryUpdateEmitter) {
    applicationReadRepository
        .findById(applicationId)
        .ifPresent(
            application -> {
              application.setApplicationDataVersion(applicationDataVersion);
              application.setApplicationVersion(applicationVersion);
              application.setModifiedAt(occurredAt);
              application.setOfficeCode(
                  ApplicationReadModelAssembler.officeCode(
                      applicationDataStore.get(applicationId, applicationDataVersion)));
              ApplicationReadModel saved = applicationReadRepository.save(application);
              queryUpdateEmitter.emit(
                  FindApplicationByIdQuery.class,
                  query -> query.applicationId().equals(applicationId),
                  saved);
            });
  }

  private void advanceCurrentStateWithStatus(
      UUID applicationId,
      long applicationVersion,
      long applicationDataVersion,
      String overallDecision,
      Instant occurredAt,
      QueryUpdateEmitter queryUpdateEmitter) {
    ApplicationStatus applicationStatus;
    DecisionValue decision = DecisionValue.valueOf(overallDecision);
    applicationStatus =
        decision.equals(DecisionValue.REFUSED)
            ? ApplicationStatus.APPLICATION_REFUSED
            : ApplicationStatus.APPLICATION_GRANTED;
    applicationReadRepository
        .findById(applicationId)
        .ifPresent(
            application -> {
              application.setApplicationDataVersion(applicationDataVersion);
              application.setApplicationVersion(applicationVersion);
              application.setModifiedAt(occurredAt);
              application.setOfficeCode(
                  ApplicationReadModelAssembler.officeCode(
                      applicationDataStore.get(applicationId, applicationDataVersion)));
              application.setStatus(applicationStatus.getValue());
              ApplicationReadModel saved = applicationReadRepository.save(application);
              queryUpdateEmitter.emit(
                  FindApplicationByIdQuery.class,
                  query -> query.applicationId().equals(applicationId),
                  saved);
            });
  }

  private Optional<String> documentFilename(ApplicationReadModel application, UUID documentId) {
    ApplicationDataId id =
        new ApplicationDataId(
            application.getApplicationId(), application.getApplicationDataVersion());
    ApplicationDataPayload data = applicationDataStore.getAll(List.of(id)).get(id);
    // Drafts have no immutable content until submission.
    Map<UUID, java.lang.String> filenames =
        data != null
            ? data.documentFilenames()
            : draftStore
                .find(application.getApplicationId())
                .map(ApplicationDraftPayload::documentFilenames)
                .orElse(Map.of());
    return java.util.Optional.ofNullable(filenames.get(documentId));
  }

  private EvidenceDocument evidenceDocument(UploadDocument document, String fileName) {
    return new EvidenceDocument(
        document.documentId(),
        document.documentType(),
        fileName,
        null,
        document.contentType(),
        document.size(),
        document.uploadedAt(),
        document.sourceService(),
        document.checksum());
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
}
