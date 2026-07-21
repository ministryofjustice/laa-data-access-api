package uk.gov.justice.laa.dstew.access.controller.individual;

import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.model.IncludedAdditionalData;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsQuery;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;

/** HTTP query adapter for individual searches. */
@RestController
@RequestMapping("/api/v0/individuals")
public class IndividualsController {

  private final QueryGateway queryGateway;
  private final GetIndividualsResponseMapper responseMapper;

  public IndividualsController(
      QueryGateway queryGateway, GetIndividualsResponseMapper responseMapper) {
    this.queryGateway = queryGateway;
    this.responseMapper = responseMapper;
  }

  /** Returns a filtered, paginated list of individuals from current application data. */
  @GetMapping
  public ResponseEntity<IndividualsResponse> getIndividuals(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @RequestParam(required = false) IncludedAdditionalData include,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) UUID applicationId,
      @RequestParam(name = "individualType", required = false) IndividualType type) {
    FindIndividualsResult result =
        queryGateway
            .query(
                new FindIndividualsQuery(
                    applicationId,
                    type == null ? null : type.name(),
                    include == IncludedAdditionalData.CLIENT_DETAILS,
                    page,
                    pageSize),
                FindIndividualsResult.class)
            .join();
    return ResponseEntity.ok(responseMapper.toResponse(result));
  }
}
