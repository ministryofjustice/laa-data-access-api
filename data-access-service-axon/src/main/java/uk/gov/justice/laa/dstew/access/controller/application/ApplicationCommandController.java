package uk.gov.justice.laa.dstew.access.controller.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;

/** HTTP command adapter for Application writes. */
@RestController
@RequestMapping("/api/v0/applications")
public class ApplicationCommandController {

  private final CommandGateway commandGateway;
  private final SubscriptionProjectionGateway projectionGateway;
  private final CreateApplicationCommandMapper commandMapper;
  private final MakeDecisionCommandMapper decisionCommandMapper;

  /** Creates the command adapter. */
  public ApplicationCommandController(
      CommandGateway commandGateway,
      SubscriptionProjectionGateway projectionGateway,
      CreateApplicationCommandMapper commandMapper,
      MakeDecisionCommandMapper decisionCommandMapper) {
    this.commandGateway = commandGateway;
    this.projectionGateway = projectionGateway;
    this.commandMapper = commandMapper;
    this.decisionCommandMapper = decisionCommandMapper;
  }

  /** Applies an overall and per-proceeding decision to an existing Application version. */
  @PatchMapping("/{id}/decision")
  public ResponseEntity<Void> makeDecision(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @PathVariable UUID id,
      @Valid @RequestBody MakeDecisionRequest request) {
    commandGateway.sendAndWait(decisionCommandMapper.toCommand(id, request));
    return ResponseEntity.noContent().build();
  }

  /** Dispatches create directly to Axon and returns 201 once the projection is readable. */
  @PostMapping
  public ResponseEntity<Void> createApplication(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @RequestHeader(value = "X-Schema-Version", required = false, defaultValue = "1") @Min(1)
          int schemaVersion,
      @Valid @RequestBody ApplicationCreateRequest request) {
    CreateApplicationCommand command = commandMapper.toCommand(request, schemaVersion);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(command.applicationId())
            .toUri();

    boolean projected =
        projectionGateway.awaitProjection(
            new FindApplicationByIdQuery(command.applicationId()),
            ApplicationReadModel.class,
            () -> dispatchWithRetry(command));

    return projected
        ? ResponseEntity.created(location).build()
        : ResponseEntity.accepted().location(location).build();
  }

  void dispatchWithRetry(CreateApplicationCommand command) {
    try {
      commandGateway.sendAndWait(command);
    } catch (ConcurrencyException | AggregateStreamCreationException first) {
      try {
        commandGateway.sendAndWait(command);
      } catch (RuntimeException retry) {
        retry.addSuppressed(first);
        throw retry;
      }
    }
  }
}
