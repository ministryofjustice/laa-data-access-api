package uk.gov.justice.laa.dstew.access.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.api.ApplicationApi;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/**
 * Controller for handling /api/v2/applications requests.
 */
@RequiredArgsConstructor
@RestController
public class ApplicationController implements ApplicationApi {

  private final ApplicationService service;

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Void> updateApplication(UUID id, ApplicationUpdateRequest applicationUpdateReq) {
    //service.updateApplication(id, applicationUpdateReq);
    return ResponseEntity.noContent().build();
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<List<Application>> getApplications() {
    return null; //ResponseEntity.ok(service.getAllApplications());
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Application> getApplicationById(UUID id) {
    return null; //ResponseEntity.ok(service.getApplication(id));
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Void> deleteApplication(UUID id) {
    //service.deleteItem(id);
    return ResponseEntity.noContent().build();
  }

  @LogMethodArguments
  @LogMethodResponse
  @Override
  public ResponseEntity<Void> createApplication(ApplicationCreateRequest applicationCreateReq) {
    UUID id = service.createApplication(applicationCreateReq);

    URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    return ResponseEntity.created(uri).build();
  }
}
