package uk.gov.justice.laa.dstew.access.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.CaseworkersApi;
import uk.gov.justice.laa.dstew.access.model.Caseworker;
import uk.gov.justice.laa.dstew.access.service.CaseworkerService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/**
 * Controller for getting the caseworkers.
 */
@RequiredArgsConstructor
@RestController
public class CaseworkerController implements CaseworkersApi {

  private final CaseworkerService caseworkerService;

  @LogMethodArguments
  @LogMethodResponse
  @Override
  public ResponseEntity<List<Caseworker>> getCaseworkers() {
    return ResponseEntity.ok(caseworkerService.getAllCaseworkers());
  }  
}
