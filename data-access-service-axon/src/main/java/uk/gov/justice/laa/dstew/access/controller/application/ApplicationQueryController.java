package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.submission.FindSubmissionByApplicationIdQuery;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionData;

/** HTTP query adapter for Application reads. */
@RestController
@RequestMapping("/api/v0/applications")
public class ApplicationQueryController {

  private final QueryGateway queryGateway;
  private final GetApplicationResponseMapper responseMapper;

  public ApplicationQueryController(
      QueryGateway queryGateway, GetApplicationResponseMapper responseMapper) {
    this.queryGateway = queryGateway;
    this.responseMapper = responseMapper;
  }

  /** Returns the current-state projection for the requested Application. */
  @GetMapping("/{id}")
  public ResponseEntity<ApplicationResponse> getApplicationById(@PathVariable UUID id) {
    ApplicationReadModel application =
        queryGateway
            .query(
                new FindApplicationByIdQuery(id),
                ResponseTypes.optionalInstanceOf(ApplicationReadModel.class))
            .join()
            .orElseThrow(
                () -> new ResourceNotFoundException("No application found with ID: " + id));
    SubmissionData payload =
        queryGateway
            .query(
                new FindSubmissionByApplicationIdQuery(id),
                ResponseTypes.optionalInstanceOf(SubmissionData.class))
            .join()
            .orElse(null);
    return ResponseEntity.ok(responseMapper.toResponse(application, payload));
  }
}
