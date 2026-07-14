package uk.gov.justice.laa.dstew.access.controller.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** HTTP command adapter for Application writes. */
@RestController
@RequestMapping("/api/v0/applications")
public class ApplicationCommandController {

  private final CommandGateway commandGateway;
  private final CreateApplicationCommandMapper commandMapper;

  public ApplicationCommandController(
      CommandGateway commandGateway, CreateApplicationCommandMapper commandMapper) {
    this.commandGateway = commandGateway;
    this.commandMapper = commandMapper;
  }

  /** Dispatches create directly to Axon and returns the pre-generated aggregate location. */
  @PostMapping
  public ResponseEntity<Void> createApplication(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @RequestHeader(value = "X-Schema-Version", required = false, defaultValue = "1") @Min(1)
          int schemaVersion,
      @Valid @RequestBody ApplicationCreateRequest request) {
    CreateApplicationCommand command = commandMapper.toCommand(request, schemaVersion);
    commandGateway.sendAndWait(command);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(command.applicationId())
            .toUri();
    return ResponseEntity.created(location).build();
  }
}
