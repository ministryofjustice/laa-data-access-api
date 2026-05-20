package uk.gov.justice.laa.dstew.access.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.api.ApplicationApi;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationNotesResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationOrderBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.applications.AssignCaseworkerService;
import uk.gov.justice.laa.dstew.access.service.applications.CreateApplicationService;
import uk.gov.justice.laa.dstew.access.service.applications.CreateNoteService;
import uk.gov.justice.laa.dstew.access.service.applications.GetAllApplicationsService;
import uk.gov.justice.laa.dstew.access.service.applications.GetAllNotesForApplicationService;
import uk.gov.justice.laa.dstew.access.service.applications.GetApplicationService;
import uk.gov.justice.laa.dstew.access.service.applications.GetCertificateService;
import uk.gov.justice.laa.dstew.access.service.applications.MakeDecisionService;
import uk.gov.justice.laa.dstew.access.service.applications.UnassignCaseworkerService;
import uk.gov.justice.laa.dstew.access.service.applications.UpdateApplicationService;
import uk.gov.justice.laa.dstew.access.service.domainevents.GetDomainEventService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper.PaginatedResult;

/** Controller for handling /api/v0/applications requests. */
@RequiredArgsConstructor
@RestController
@ExcludeFromGeneratedCodeCoverage
public class ApplicationController implements ApplicationApi {

  private final CreateApplicationService createApplicationService;
  private final UpdateApplicationService updateApplicationService;
  private final GetApplicationService getApplicationsService;
  private final GetAllApplicationsService applicationSummaryService;
  private final GetCertificateService certificateService;
  private final AssignCaseworkerService assignCaseworkerService;
  private final UnassignCaseworkerService unassignCaseworkerService;
  private final MakeDecisionService makeDecisionService;
  private final GetAllNotesForApplicationService getNotesService;
  private final CreateNoteService createNoteService;
  private final GetDomainEventService getDomainEventsService;

  @LogMethodArguments
  @LogMethodResponse
  @Override
  public ResponseEntity<Void> createApplication(
      @NotNull ServiceName serviceName, @Valid ApplicationCreateRequest applicationCreateReq) {
    UUID id = createApplicationService.createApplication(applicationCreateReq);

    URI uri =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    return ResponseEntity.created(uri).build();
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Void> updateApplication(
      @NotNull ServiceName serviceName,
      UUID id,
      @Valid ApplicationUpdateRequest applicationUpdateReq) {
    updateApplicationService.updateApplication(id, applicationUpdateReq);
    return ResponseEntity.noContent().build();
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<ApplicationSummaryResponse> getApplications(
      ServiceName serviceName,
      ApplicationStatus status,
      String laaReference,
      String clientFirstName,
      String clientLastName,
      LocalDate clientDateOfBirth,
      UUID userId,
      Boolean isAutoGranted,
      MatterType matterType,
      ApplicationSortBy sortBy,
      ApplicationOrderBy orderBy,
      Integer page,
      Integer pageSize) {

    PaginatedResult<ApplicationSummary> result =
        applicationSummaryService.getAllApplications(
            status,
            laaReference,
            clientFirstName,
            clientLastName,
            clientDateOfBirth,
            userId,
            isAutoGranted,
            matterType,
            sortBy,
            orderBy,
            page,
            pageSize);

    List<ApplicationSummary> applications = result.page().stream().toList();
    PagingResponse pagingResponse = new PagingResponse();
    pagingResponse.setPage(result.requestedPage());
    pagingResponse.pageSize(result.requestedPageSize());
    pagingResponse.totalRecords((int) result.page().getTotalElements());
    pagingResponse.itemsReturned(applications.size());

    ApplicationSummaryResponse response = new ApplicationSummaryResponse();
    response.setApplications(applications);
    response.setPaging(pagingResponse);

    return ResponseEntity.ok(response);
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<ApplicationResponse> getApplicationById(ServiceName serviceName, UUID id) {
    return ResponseEntity.ok(getApplicationsService.getApplication(id));
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> assignCaseworker(
      @NotNull ServiceName serviceName, @Valid CaseworkerAssignRequest request) {
    assignCaseworkerService.assignCaseworker(
        request.getCaseworkerId(), request.getApplicationIds(), request.getEventHistory());
    return ResponseEntity.ok().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> unassignCaseworker(
      @NotNull ServiceName serviceName, UUID id, @Valid CaseworkerUnassignRequest request) {

    unassignCaseworkerService.unassignCaseworker(id, request.getEventHistory());

    return ResponseEntity.ok().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<ApplicationHistoryResponse> getApplicationHistory(
      @NotNull ServiceName serviceName,
      UUID applicationId,
      @Valid List<DomainEventType> eventType) {
    var events = getDomainEventsService.getEvents(applicationId, eventType);
    return ResponseEntity.ok(ApplicationHistoryResponse.builder().events(events).build());
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> makeDecision(
      @NotNull ServiceName serviceName, UUID applicationId, @Valid MakeDecisionRequest request) {

    makeDecisionService.makeDecision(applicationId, request);

    return ResponseEntity.noContent().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> createApplicationNotes(
      @NotNull ServiceName serviceName, UUID applicationId, @Valid CreateNoteRequest request) {

    createNoteService.createApplicationNote(applicationId, request);

    return ResponseEntity.noContent().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<ApplicationNotesResponse> getApplicationNotes(
      @NotNull ServiceName serviceName, UUID applicationId) {
    return ResponseEntity.ok(getNotesService.getApplicationNotes(applicationId));
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Map<String, Object>> getCertificate(
      @NotNull ServiceName serviceName, UUID applicationId) {
    return ResponseEntity.ok(certificateService.getCertificate(applicationId));
  }
}
