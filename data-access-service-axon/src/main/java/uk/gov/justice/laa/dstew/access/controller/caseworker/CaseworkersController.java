package uk.gov.justice.laa.dstew.access.controller.caseworker;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.CaseworkersApi;
import uk.gov.justice.laa.dstew.access.model.CaseworkerResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.caseworkers.GetCaseworkersService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/** HTTP query adapter for caseworker lookups. */
@RestController
public class CaseworkersController implements CaseworkersApi {

  private final GetCaseworkersService caseworkersService;

  public CaseworkersController(GetCaseworkersService caseworkersService) {
    this.caseworkersService = caseworkersService;
  }

  /** Returns all caseworkers. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<List<CaseworkerResponse>> getCaseworkers(ServiceName serviceName) {
    return ResponseEntity.ok(caseworkersService.getCaseworkers());
  }
}
