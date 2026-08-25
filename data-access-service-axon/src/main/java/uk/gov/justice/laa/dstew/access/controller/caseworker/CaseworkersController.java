package uk.gov.justice.laa.dstew.access.controller.caseworker;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.model.CaseworkerResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.caseworkers.GetCaseworkersService;

/** HTTP query adapter for caseworker lookups. */
@RestController
@RequestMapping("/api/v0/caseworkers")
public class CaseworkersController {

  private final GetCaseworkersService caseworkersService;

  public CaseworkersController(GetCaseworkersService caseworkersService) {
    this.caseworkersService = caseworkersService;
  }

  /** Returns all caseworkers. */
  @GetMapping
  public ResponseEntity<List<CaseworkerResponse>> getCaseworkers(
      @RequestHeader("X-Service-Name") ServiceName serviceName) {
    return ResponseEntity.ok(caseworkersService.getCaseworkers());
  }
}
