package uk.gov.justice.laa.dstew.access.controller.synchronousapplication;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.CreateDraftPriorAuthorityCommand;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.UpdateDraftPriorAuthorityCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** HTTP command adapter for prior authority drafts nested within an application. */
@RestController
@RequestMapping("/api/v0/synchronous-applications/{applicationId}/prior-authorities")
public class PriorAuthorityCommandController {

  private final CommandGateway commandGateway;

  public PriorAuthorityCommandController(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;
  }

  /** Creates a new prior authority draft against the application and returns 201 Created. */
  @PostMapping
  public ResponseEntity<Void> createPriorAuthority(
      @PathVariable UUID applicationId, @RequestBody Map<String, Object> content) {
    UUID priorAuthorityId;
    try {
      priorAuthorityId =
          commandGateway.sendAndWait(new CreateDraftPriorAuthorityCommand(applicationId, content));
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
      commandGateway.sendAndWait(
          new UpdateDraftPriorAuthorityCommand(applicationId, priorAuthorityId, content));
    } catch (AggregateNotFoundException e) {
      throw applicationNotFound(applicationId, e);
    }
    return ResponseEntity.noContent().build();
  }

  private static ResourceNotFoundException applicationNotFound(
      UUID applicationId, RuntimeException cause) {
    ResourceNotFoundException ex =
        new ResourceNotFoundException("No synchronous application found with ID: " + applicationId);
    ex.initCause(cause);
    return ex;
  }
}
