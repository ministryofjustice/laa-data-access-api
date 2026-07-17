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
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.CreateDraftApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.UpdateDraftApplicationCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** HTTP command adapter for mutable draft applications. */
@RestController
@RequestMapping("/api/v0/draft-applications")
public class DraftApplicationCommandController {

  private final CommandGateway commandGateway;

  public DraftApplicationCommandController(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;
  }

  /** Creates a new draft application and returns 201 Created with a Location header. */
  @PostMapping
  public ResponseEntity<Void> createDraftApplication(@RequestBody Map<String, Object> content) {
    UUID draftApplicationId = UUID.randomUUID();
    commandGateway.sendAndWait(new CreateDraftApplicationCommand(draftApplicationId, content));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(draftApplicationId)
            .toUri();
    return ResponseEntity.created(location).build();
  }

  /** Updates an existing draft application in place and returns 204 No Content. */
  @PutMapping("/{draftApplicationId}")
  public ResponseEntity<Void> updateDraftApplication(
      @PathVariable UUID draftApplicationId, @RequestBody Map<String, Object> content) {
    try {
      commandGateway.sendAndWait(new UpdateDraftApplicationCommand(draftApplicationId, content));
    } catch (AggregateNotFoundException e) {
      ResourceNotFoundException ex =
          new ResourceNotFoundException(
              "No draft application found with ID: " + draftApplicationId);
      ex.initCause(e);
      throw ex;
    }
    return ResponseEntity.noContent().build();
  }
}
