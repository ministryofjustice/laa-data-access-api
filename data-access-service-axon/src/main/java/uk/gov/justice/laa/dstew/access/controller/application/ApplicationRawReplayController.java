package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.replay.ApplicationRawReplayService;

/**
 * Diagnostic/test endpoint that reconstructs an {@link ApplicationReadModel} directly from the raw
 * Axon event store, bypassing the Axon query API entirely.
 *
 * <p>This is <strong>not</strong> part of the standard read API — it exists to verify (or, in a
 * recovery scenario, cross-check) that the persisted event stream alone is sufficient to
 * reconstruct current state, independent of the running projection.
 */
@RestController
@RequestMapping("/api/v0/applications")
public class ApplicationRawReplayController {

  private final ApplicationRawReplayService rawReplayService;
  private final GetApplicationResponseMapper responseMapper;

  /** Constructs the controller with its raw-replay service and response mapper. */
  public ApplicationRawReplayController(
      ApplicationRawReplayService rawReplayService, GetApplicationResponseMapper responseMapper) {
    this.rawReplayService = rawReplayService;
    this.responseMapper = responseMapper;
  }

  /** Replays the raw event stream for the given Application and returns the rebuilt state. */
  @GetMapping("/{id}/raw-replay")
  public ResponseEntity<ApplicationResponse> getRawReplayedApplication(@PathVariable UUID id) {
    ApplicationReadModel application =
        rawReplayService
            .replay(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("No application found with ID: " + id));
    return ResponseEntity.ok(responseMapper.toResponse(application));
  }
}
