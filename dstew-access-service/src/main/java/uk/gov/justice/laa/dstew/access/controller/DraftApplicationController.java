package uk.gov.justice.laa.dstew.access.controller;

import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.minidev.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.api.DraftapplicationApi;
import uk.gov.justice.laa.dstew.access.model.DraftApplication;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationCreateReq;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationUpdateReq;
import uk.gov.justice.laa.dstew.access.service.DraftApplicationService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/**
 * Controller for handling /api/v2/default-applications requests.
 */
@RequiredArgsConstructor
@RestController
public class DraftApplicationController implements DraftapplicationApi {

  final DraftApplicationService service;

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Void> createDraftApplication(DraftApplicationCreateReq draftApplicationCreateReq) {
    UUID id = service.createApplication(draftApplicationCreateReq);

    URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(id).toUri();
    return ResponseEntity.created(uri).build();
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<DraftApplication> getDraftApplicationById(UUID id) {
    return ResponseEntity.ok(service.getApplicationById(id));
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Void> updateDraftApplication(UUID id, DraftApplicationUpdateReq applicationUpdateReq) {
    service.updateApplication(id, applicationUpdateReq);
    return ResponseEntity.noContent().build();
  }
}