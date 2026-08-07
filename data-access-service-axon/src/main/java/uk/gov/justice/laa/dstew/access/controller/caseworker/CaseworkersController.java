package uk.gov.justice.laa.dstew.access.controller.caseworker;

import java.util.List;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.model.CaseworkerResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.caseworker.FindCaseworkersQuery;
import uk.gov.justice.laa.dstew.access.query.caseworker.FindCaseworkersResult;

/** HTTP query adapter for caseworker lookups. */
@RestController
@RequestMapping("/api/v0/caseworkers")
public class CaseworkersController {

  private final QueryGateway queryGateway;
  private final GetCaseworkersResponseMapper responseMapper;

  public CaseworkersController(
      QueryGateway queryGateway, GetCaseworkersResponseMapper responseMapper) {
    this.queryGateway = queryGateway;
    this.responseMapper = responseMapper;
  }

  /** Returns all caseworkers. */
  @GetMapping
  public ResponseEntity<List<CaseworkerResponse>> getCaseworkers(
      @RequestHeader("X-Service-Name") ServiceName serviceName) {
    FindCaseworkersResult result =
        queryGateway.query(new FindCaseworkersQuery(), FindCaseworkersResult.class).join();
    return ResponseEntity.ok(responseMapper.toResponse(result));
  }
}
