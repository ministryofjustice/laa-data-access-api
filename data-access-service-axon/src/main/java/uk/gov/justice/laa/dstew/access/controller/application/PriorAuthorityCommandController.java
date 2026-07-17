package uk.gov.justice.laa.dstew.access.controller.application;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.AggregateEntityNotFoundException;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.application.PriorAuthorityService;

/**
 * HTTP command adapter for prior authorities nested within an application. Prior authority is a
 * post-submission concern: it can only be created against an already-submitted application, has its
 * own {@code DRAFT} then {@code SUBMITTED} lifecycle, and is addressed by {@code priorAuthorityId}.
 * The draft body is persisted by {@link PriorAuthorityService}; the events stay PII-free pointers.
 */
@RestController
@RequestMapping("/api/v0/applications/{applicationId}/prior-authorities")
public class PriorAuthorityCommandController {

  private final PriorAuthorityService priorAuthorityService;

  public PriorAuthorityCommandController(PriorAuthorityService priorAuthorityService) {
    this.priorAuthorityService = priorAuthorityService;
  }

  /** Creates a new prior authority draft against the application and returns 201 Created. */
  @PostMapping
  public ResponseEntity<Void> createPriorAuthority(
      @PathVariable UUID applicationId, @RequestBody Map<String, Object> content) {
    UUID priorAuthorityId;
    try {
      priorAuthorityId = priorAuthorityService.createDraft(applicationId, content);
    } catch (AggregateNotFoundException e) {
      throw applicationNotFound(applicationId, e);
    }
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(priorAuthorityId)
            .toUri();
    return ResponseEntity.created(location).build();
  }

  /** Updates an existing prior authority draft in place and returns 204 No Content. */
  @PutMapping("/{priorAuthorityId}")
  public ResponseEntity<Void> updatePriorAuthority(
      @PathVariable UUID applicationId,
      @PathVariable UUID priorAuthorityId,
      @RequestBody Map<String, Object> content) {
    try {
      priorAuthorityService.updateDraft(applicationId, priorAuthorityId, content);
    } catch (AggregateNotFoundException e) {
      throw applicationNotFound(applicationId, e);
    } catch (AggregateEntityNotFoundException e) {
      throw priorAuthorityNotFound(priorAuthorityId, e);
    }
    return ResponseEntity.noContent().build();
  }

  /** Submits an existing prior authority draft and returns 204 No Content. */
  @PostMapping("/{priorAuthorityId}/submit")
  public ResponseEntity<Void> submitPriorAuthority(
      @PathVariable UUID applicationId, @PathVariable UUID priorAuthorityId) {
    try {
      priorAuthorityService.submit(applicationId, priorAuthorityId);
    } catch (AggregateNotFoundException e) {
      throw applicationNotFound(applicationId, e);
    } catch (AggregateEntityNotFoundException e) {
      throw priorAuthorityNotFound(priorAuthorityId, e);
    }
    return ResponseEntity.noContent().build();
  }

  private static ResourceNotFoundException applicationNotFound(
      UUID applicationId, RuntimeException cause) {
    ResourceNotFoundException ex =
        new ResourceNotFoundException("No application found with ID: " + applicationId);
    ex.initCause(cause);
    return ex;
  }

  private static ResourceNotFoundException priorAuthorityNotFound(
      UUID priorAuthorityId, RuntimeException cause) {
    ResourceNotFoundException ex =
        new ResourceNotFoundException("No prior authority found with ID: " + priorAuthorityId);
    ex.initCause(cause);
    return ex;
  }
}
