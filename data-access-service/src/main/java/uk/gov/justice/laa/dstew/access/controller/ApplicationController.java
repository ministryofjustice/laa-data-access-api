package uk.gov.justice.laa.dstew.access.controller;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.api.ApplicationApi;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationOrderBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.Paging;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.service.ApplicationSummaryService;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;


/**
 * Controller for handling /api/v0/applications requests.
 */
@RequiredArgsConstructor
@RestController
@ExcludeFromGeneratedCodeCoverage
public class ApplicationController implements ApplicationApi {

  private final ApplicationService service;
  private final ApplicationSummaryService summaryService;
  private final DomainEventService domainService;

  @LogMethodArguments
  @LogMethodResponse
  @Override
  public ResponseEntity<Void> createApplication(
          @NotNull ServiceName serviceName,
          @Valid ApplicationCreateRequest applicationCreateReq) {
    UUID id = service.createApplication(applicationCreateReq);

    URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    return ResponseEntity.created(uri).build();
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Void> updateApplication(
          @NotNull ServiceName serviceName,
          UUID id,
          @Valid ApplicationUpdateRequest applicationUpdateReq) {
    service.updateApplication(id, applicationUpdateReq);
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

    Page<ApplicationSummary> applicationsReturned =
            summaryService.getAllApplications(
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

    ApplicationSummaryResponse response = new ApplicationSummaryResponse();
    List<ApplicationSummary> applications = applicationsReturned.stream().toList();
    Paging paging = new Paging();
    response.setApplications(applications);
    paging.setPage(applicationsReturned.getNumber() + 1); // Convert zero-based to one-based
    paging.pageSize(applicationsReturned.getSize());
    paging.totalRecords((int) applicationsReturned.getTotalElements());
    paging.itemsReturned(applications.size());
    response.setPaging(paging);

    return ResponseEntity.ok(response);
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Application> getApplicationById(ServiceName serviceName, UUID id) {
    return ResponseEntity.ok(service.getApplication(id));
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> assignCaseworker(@NotNull ServiceName serviceName, @Valid CaseworkerAssignRequest request) {
    service.assignCaseworker(request.getCaseworkerId(),
                              request.getApplicationIds(),
                              request.getEventHistory());
    return ResponseEntity.ok().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> unassignCaseworker(
          @NotNull ServiceName serviceName,
          UUID id,
          @Valid CaseworkerUnassignRequest request) {

    service.unassignCaseworker(id, request.getEventHistory());

    return ResponseEntity.ok().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<ApplicationHistoryResponse> getApplicationHistory(
          @NotNull ServiceName serviceName,
          UUID applicationId,
          @Valid List<DomainEventType> eventType) {
    var events = domainService.getEvents(applicationId, eventType);
    return ResponseEntity.ok(ApplicationHistoryResponse.builder()
                                                 .events(events)
                                                 .build());
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> makeDecision(@NotNull ServiceName serviceName,
                                           UUID applicationId,
                                           @Valid MakeDecisionRequest request) {

    service.makeDecision(applicationId, request);

    return ResponseEntity.noContent().build();
  }
}
