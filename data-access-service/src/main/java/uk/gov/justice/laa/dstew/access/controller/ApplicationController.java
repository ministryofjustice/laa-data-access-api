package uk.gov.justice.laa.dstew.access.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.api.ApplicationApi;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.model.Paging;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.service.ApplicationSummaryService;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;
import uk.gov.justice.laa.dstew.access.specification.DomainEventSpecification;

/**
 * Controller for handling /api/v0/applications requests.
 */
@RequiredArgsConstructor
@RestController
public class ApplicationController implements ApplicationApi {

  private final ApplicationService service;
  private final ApplicationSummaryService summaryService;
  private final DomainEventService domainService;

  @LogMethodArguments
  @LogMethodResponse
  @Override
  public ResponseEntity<Void> createApplication(ApplicationCreateRequest applicationCreateReq) {
    UUID id = service.createApplication(applicationCreateReq);

    URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    return ResponseEntity.created(uri).build();
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Void> updateApplication(UUID id, ApplicationUpdateRequest applicationUpdateReq) {
    service.updateApplication(id, applicationUpdateReq);
    return ResponseEntity.noContent().build();
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<ApplicationSummaryResponse> getApplications(
          ApplicationStatus status,
          String reference,
          String firstName,
          String lastName,
          UUID userId,
          Integer page,
          Integer pageSize) {
    page = (page == null || page < 1) ? 1 : page;

    Page<ApplicationSummary> applicationsReturned =
            summaryService.getAllApplications(
                    status,
                    reference,
                    firstName,
                    lastName,
                    userId,
                    page - 1, pageSize);

    ApplicationSummaryResponse response = new ApplicationSummaryResponse();
    List<ApplicationSummary> applications = applicationsReturned.stream().toList();
    Paging paging = new Paging();
    response.setApplications(applications);
    paging.setPage(page);
    paging.pageSize(pageSize);
    paging.totalRecords((int) applicationsReturned.getTotalElements());
    paging.itemsReturned(applications.size());
    response.setPaging(paging);

    return ResponseEntity.ok(response);
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Application> getApplicationById(UUID id) {
    return ResponseEntity.ok(service.getApplication(id));
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> assignCaseworker(CaseworkerAssignRequest request) {
    service.assignCaseworker(request.getCaseworkerId(),
                              request.getApplicationIds(),
                              request.getEventHistory());
    return ResponseEntity.ok().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> unassignCaseworker(UUID id, CaseworkerUnassignRequest request) {

    service.unassignCaseworker(id, request.getEventHistory());

    return ResponseEntity.ok().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<ApplicationHistoryResponse> getApplicationHistory(UUID applicationId,
      @Valid List<DomainEventType> eventType) {
    var events = domainService.getEvents(applicationId, eventType);
    return ResponseEntity.ok(ApplicationHistoryResponse.builder()
                                                 .events(events)
                                                 .build());
  }
}
