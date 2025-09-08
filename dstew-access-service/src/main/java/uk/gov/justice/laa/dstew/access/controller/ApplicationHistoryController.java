package uk.gov.justice.laa.dstew.access.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.ApplicationHistoryApi;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistory;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryCreateRequest;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/**
 * Controller for handling application requests.
 */
@RestController
@RequiredArgsConstructor
public class ApplicationHistoryController implements ApplicationHistoryApi {

  private final ApplicationService service;


  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Void> recordApplicationHistory(
      final UUID id,
      final ApplicationHistoryCreateRequest applicationHistoryCreateReq) {
    return null; // TODO: implement or remove.
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<List<ApplicationHistory>> getApplicationHistory(final UUID applicationId) {
    return ResponseEntity.ok(service.getAllApplicationHistory(applicationId));
  }

  @LogMethodResponse
  @LogMethodArguments
  @Override
  public ResponseEntity<ApplicationHistory> getApplicationHistoryById(final UUID applicationId, final UUID id) {
    return null; // TODO: implement or remove.
  }

  @LogMethodResponse
  @LogMethodArguments
  @Override
  public ResponseEntity<ApplicationHistory> getLatestApplicationHistory(final UUID applicationId) {
    return ResponseEntity.ok(service.getApplicationsLatestHistory(applicationId));
  }
}
