package uk.gov.justice.laa.dstew.access.controller.application;

import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.application.DraftApplicationService;
import uk.gov.justice.laa.dstew.access.service.application.SubmitApplicationService;

/**
 * HTTP command adapter for Application writes.
 *
 * <p>An application begins life as a mutable draft ({@code PUT /{id}}, create-or-overwrite) and is
 * later sealed by an explicit submit action ({@code POST /{id}/submit}). There is no one-shot
 * create+submit. The application id is client-supplied for now (interim).
 */
@RestController
@RequestMapping("/api/v0/applications")
public class ApplicationCommandController {

  private final DraftApplicationService draftApplicationService;
  private final SubmitApplicationService submitApplicationService;

  /** Wires the draft and submit application services. */
  public ApplicationCommandController(
      DraftApplicationService draftApplicationService,
      SubmitApplicationService submitApplicationService) {
    this.draftApplicationService = draftApplicationService;
    this.submitApplicationService = submitApplicationService;
  }

  /**
   * Creates or overwrites the draft for the given application id and returns 204 No Content. The
   * body is a raw, unvalidated draft payload; validation happens at submit. Returns 409 if the
   * application has already been submitted.
   */
  @PutMapping("/{id}")
  public ResponseEntity<Void> putDraftApplication(
      @PathVariable UUID id, @RequestBody Map<String, Object> content) {
    draftApplicationService.putDraft(id, content);
    return ResponseEntity.noContent().build();
  }

  /**
   * Submits the stored draft for the given application id and returns 204 No Content. Seals the
   * draft body already held in the drafts table; carries no body. Returns 404 if no draft exists,
   * 409 if already submitted, 400 if the draft fails validation.
   */
  @PostMapping("/{id}/submit")
  public ResponseEntity<Void> submitApplication(
      @PathVariable UUID id,
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @RequestHeader(value = "X-Schema-Version", required = false, defaultValue = "1") @Min(1)
          int schemaVersion) {
    try {
      submitApplicationService.submit(id, schemaVersion);
    } catch (AggregateNotFoundException e) {
      ResourceNotFoundException ex =
          new ResourceNotFoundException("No draft application found with ID: " + id);
      ex.initCause(e);
      throw ex;
    }
    return ResponseEntity.noContent().build();
  }
}
