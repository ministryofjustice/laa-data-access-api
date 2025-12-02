package uk.gov.justice.laa.dstew.access.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.api.ApplicationApi;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponsePaging;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.service.ApplicationSummaryService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/**
 * Controller for handling /api/v0/applications requests.
 */
@RequiredArgsConstructor
@RestController
public class ApplicationController implements ApplicationApi {

  private final ApplicationService service;
  private final ApplicationSummaryService summaryService;

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

    Page<ApplicationSummary> applicationsReturned =
            summaryService.getAllApplications(
                    status,
                    reference,
                    firstName,
                    lastName,
                    userId,
                    page, pageSize);
    ApplicationSummaryResponse response = new ApplicationSummaryResponse();
    ApplicationSummaryResponsePaging responsePageDetails = new ApplicationSummaryResponsePaging();
    response.setPaging(responsePageDetails);
    List<ApplicationSummary> applications = applicationsReturned.stream().toList();
    response.setApplications(applications);
    responsePageDetails.setPage(page);
    responsePageDetails.pageSize(pageSize);
    responsePageDetails.totalRecords((int) applicationsReturned.getTotalElements());
    responsePageDetails.itemsReturned(applications.size());

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
  public ResponseEntity<Void> assignCaseworker(UUID id, CaseworkerAssignRequest request) {
    service.assignCaseworker(id, request.getCaseworkerId());
    return ResponseEntity.ok().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> unassignCaseworker(UUID id) {
    service.unassignCaseworker(id);
    return ResponseEntity.ok().build();
  }

}
