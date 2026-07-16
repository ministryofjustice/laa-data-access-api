package uk.gov.justice.laa.dstew.access.controller.synchronousapplication;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.CreateSynchronousApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.validation.DuplicateApplyApplicationIdException;

/** HTTP command adapter for SynchronousApplication writes. */
@RestController
@RequestMapping("/api/v0/synchronous-applications")
public class SynchronousApplicationCommandController {

  private final CommandGateway commandGateway;
  private final CreateSynchronousApplicationCommandMapper commandMapper;

  public SynchronousApplicationCommandController(
      CommandGateway commandGateway, CreateSynchronousApplicationCommandMapper commandMapper) {
    this.commandGateway = commandGateway;
    this.commandMapper = commandMapper;
  }

  /** Dispatches create synchronously and returns 201 Created with a Location header. */
  @PostMapping
  public ResponseEntity<Void> createApplication(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @RequestHeader(value = "X-Schema-Version", required = false, defaultValue = "1") @Min(1)
          int schemaVersion,
      @Valid @RequestBody ApplicationCreateRequest request) {
    CreateSynchronousApplicationCommand command = commandMapper.toCommand(request, schemaVersion);
    UUID id;
    try {
      id = commandGateway.sendAndWait(command);
    } catch (AggregateStreamCreationException | ConcurrencyException e) {
      DuplicateApplyApplicationIdException ex =
          new DuplicateApplyApplicationIdException(command.applyApplicationId());
      ex.initCause(e);
      throw ex;
    }
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    return ResponseEntity.created(location).build();
  }
}
