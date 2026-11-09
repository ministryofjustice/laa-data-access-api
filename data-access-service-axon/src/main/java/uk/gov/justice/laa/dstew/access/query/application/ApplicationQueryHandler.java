package uk.gov.justice.laa.dstew.access.query.application;

import static uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus.APPLICATION_SUBMITTED;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationNote;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummarySpecification;

/**
 * Answers Application queries. Single-application reads load the sourced aggregate; the list page
 * and the stalled-assessment scan read the application summary.
 */
@Component
@RequiredArgsConstructor
public class ApplicationQueryHandler {

  private final ApplicationReadQueryGateway applicationReadQueryGateway;
  private final ApplicationSummaryAccessPolicy summaryAccessPolicy;
  private final ApplicationSummaryReadRepository summaryRepository;
  private final ApplicationReadModelAssembler assembler;

  /** Returns the Application and its related data, or {@code null} if absent or not visible. */
  @QueryHandler
  public @Nullable ApplicationDetailResult handle(
      FindApplicationDetailQuery query,
      @InjectEntity(idProperty = "applicationId") @Nullable ApplicationAggregate aggregate) {
    return assembler
        .fromAggregate(aggregate, query.accessScope())
        .map(assembler::assembleDetail)
        .orElse(null);
  }

  /**
   * Returns a live document with its filename when available, or null if absent. Before submission
   * filenames are held with the draft; afterwards they are part of the submitted content.
   */
  @QueryHandler
  public @Nullable EvidenceDocument handle(
      FindApplicationDocumentQuery query,
      @InjectEntity(idProperty = "applicationId") @Nullable ApplicationAggregate aggregate) {
    if (aggregate == null) {
      return null;
    }
    ApplicationState state = aggregate.getState();
    return state.getUploadedDocuments().stream()
        .filter(document -> document.documentId().equals(query.documentId()))
        .filter(document -> !document.deleted())
        .findFirst()
        .map(document -> evidenceDocument(document, documentFilename(state, document.documentId())))
        .orElse(null);
  }

  /** Returns the requested Application, or {@code null} if absent or not visible. */
  @QueryHandler
  public @Nullable ApplicationReadModel handle(
      FindApplicationByIdQuery query,
      @InjectEntity(idProperty = "applicationId") @Nullable ApplicationAggregate aggregate) {
    return assembler.fromAggregate(aggregate, query.accessScope()).orElse(null);
  }

  /**
   * Returns all notes for the requested Application, ordered by creation time ascending, or {@code
   * null} if the Application is absent or not visible.
   */
  @QueryHandler
  public @Nullable ApplicationNotesResult handle(
      FindNotesForApplicationQuery query,
      @InjectEntity(idProperty = "applicationId") @Nullable ApplicationAggregate aggregate) {
    return assembler
        .fromAggregate(aggregate, query.accessScope())
        .map(application -> new ApplicationNotesResult(notes(aggregate)))
        .orElse(null);
  }

  /**
   * Returns a paginated, filtered page of application summaries.
   *
   * <p>Filtering, sorting, counting and paging run in the database on {@code application_summary},
   * which also holds every field the list shows. Group membership and linked prior authorities are
   * batch-fetched for the page.
   */
  @QueryHandler
  public FindAllApplicationsResult handle(FindAllApplicationsQuery query) {
    Sort sort = buildSort(query.sortBy(), query.orderBy());
    Pageable pageable = PageRequest.of(Math.max(0, query.page() - 1), query.pageSize(), sort);

    Page<ApplicationSummaryReadModel> page =
        applicationReadQueryGateway.findApplicationIndexPage(
            ApplicationSummarySpecification.from(query),
            pageable,
            summaryAccessPolicy.restrictionFor(query.accessScope()));
    List<ApplicationSummaryReadModel> content = page.getContent();

    Map<UUID, LinkedApplicationGroupReadModel> groupsByGroupId =
        assembler.fetchGroups(
            content.stream().map(ApplicationSummaryReadModel::getLinkedGroupId).toList());

    return new FindAllApplicationsResult(
        content,
        groupsByGroupId,
        assembler.fetchLinkedMemberDetails(groupsByGroupId.values()),
        assembler.fetchPriorAuthorities(
            content.stream().map(ApplicationSummaryReadModel::getApplicationId).toList()),
        page.getTotalElements(),
        query.page(),
        query.pageSize());
  }

  /** Returns old submitted Applications that still have no automatic-assessment outcome. */
  @QueryHandler
  public StalledAssessments handle(FindStalledAssessmentsQuery query) {
    return new StalledAssessments(
        summaryRepository
            .findAllByStatusAndAutoGrantedAndSubmittedAtBefore(
                APPLICATION_SUBMITTED.name(), AutoGrantedState.PENDING, query.submittedBefore())
            .stream()
            .map(
                summary ->
                    new StalledAssessment(
                        summary.getApplicationId(),
                        summary.getStreamVersion(),
                        summary.getSubmittedAt()))
            .toList());
  }

  private static @Nullable String documentFilename(ApplicationState state, UUID documentId) {
    if (!state.getContent().isErased()) {
      return state.getContent().value().documentFilenames().get(documentId);
    }
    Erasable<String> draftFilename = state.getDraftDocumentFilenames().get(documentId);
    return draftFilename == null ? null : draftFilename.orElse(null);
  }

  private static List<ApplicationNote> notes(ApplicationAggregate aggregate) {
    return aggregate.getState().getContent().value().notes();
  }

  private EvidenceDocument evidenceDocument(DocumentMetadata document, String fileName) {
    return new EvidenceDocument(
        document.documentId(),
        document.documentType(),
        fileName,
        null,
        document.contentType(),
        document.size(),
        document.uploadedAt(),
        document.sourceService(),
        document.checksum(),
        document.fileSuffix());
  }

  private Sort buildSort(String sortBy, String orderBy) {
    String property = "LAST_UPDATED_DATE".equalsIgnoreCase(sortBy) ? "modifiedAt" : "submittedAt";
    Sort.Direction direction =
        "DESC".equalsIgnoreCase(orderBy) ? Sort.Direction.DESC : Sort.Direction.ASC;
    return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "applicationId"));
  }
}
