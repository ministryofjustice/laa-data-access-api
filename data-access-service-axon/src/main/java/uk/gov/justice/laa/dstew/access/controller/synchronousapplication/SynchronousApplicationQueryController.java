package uk.gov.justice.laa.dstew.access.controller.synchronousapplication;

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
import uk.gov.justice.laa.dstew.access.query.submission.FindSubmissionByApplicationIdQuery;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionData;
import uk.gov.justice.laa.dstew.access.query.synchronousapplication.FindSynchronousApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.synchronousapplication.SynchronousApplicationReadModel;

/** HTTP query adapter for SynchronousApplication reads. */
@RestController
@RequestMapping("/api/v0/synchronous-applications")
public class SynchronousApplicationQueryController {

  private final QueryGateway queryGateway;
  private final GetSynchronousApplicationResponseMapper responseMapper;

  public SynchronousApplicationQueryController(
      QueryGateway queryGateway, GetSynchronousApplicationResponseMapper responseMapper) {
    this.queryGateway = queryGateway;
    this.responseMapper = responseMapper;
  }

  /** Returns the current-state projection for the requested SynchronousApplication. */
  @GetMapping("/{id}")
  public ResponseEntity<ApplicationResponse> getSynchronousApplicationById(@PathVariable UUID id) {
    SynchronousApplicationReadModel application =
        queryGateway
            .query(
                new FindSynchronousApplicationByIdQuery(id),
                ResponseTypes.optionalInstanceOf(SynchronousApplicationReadModel.class))
            .join()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No synchronous application found with ID: " + id));
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
