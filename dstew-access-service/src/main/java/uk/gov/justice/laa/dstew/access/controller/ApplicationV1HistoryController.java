package uk.gov.justice.laa.dstew.access.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.ApplicationV1HistoryApi;
import uk.gov.justice.laa.dstew.access.model.ApplicationV1History;
import uk.gov.justice.laa.dstew.access.model.ApplicationV1HistoryCreateReq;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/**
 * Controller for handling application requests.
 */
@RestController
@RequiredArgsConstructor
public class ApplicationV1HistoryController implements ApplicationV1HistoryApi {

  private final ApplicationService service;


  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Void> recordApplicationHistory(
      final UUID id,
      final ApplicationV1HistoryCreateReq applicationHistoryCreateReq) {
    return null; // TODO: implement or remove.
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<List<ApplicationV1History>> getApplicationHistory(final UUID applicationId) {
    return ResponseEntity.ok(service.getAllApplicationHistory(applicationId));
  }

  @LogMethodResponse
  @LogMethodArguments
  @Override
  public ResponseEntity<ApplicationV1History> getApplicationHistoryById(final UUID applicationId, final UUID id) {
    return null; // TODO: implement or remove.
  }

  @LogMethodResponse
  @LogMethodArguments
  @Override
  public ResponseEntity<ApplicationV1History> getLatestApplicationHistory(final UUID applicationId) {
    return ResponseEntity.ok(service.getApplicationsLatestHistory(applicationId));
  }
}
