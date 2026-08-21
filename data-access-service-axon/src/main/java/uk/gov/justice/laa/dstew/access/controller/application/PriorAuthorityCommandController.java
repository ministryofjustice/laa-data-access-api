package uk.gov.justice.laa.dstew.access.controller.application;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityUseCase;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** HTTP command adapter for prior-authority submissions. */
@RestController
@RequestMapping("/api/v0/applications")
public class PriorAuthorityCommandController {

  private final CreatePriorAuthorityUseCase useCase;
  private final CreatePriorAuthorityCommandMapper commandMapper;

  /** Creates the command adapter. */
  public PriorAuthorityCommandController(
      CreatePriorAuthorityUseCase useCase, CreatePriorAuthorityCommandMapper commandMapper) {
    this.useCase = useCase;
    this.commandMapper = commandMapper;
  }

  /** Dispatches create-prior-authority and returns 201 once the projection is readable. */
  @PostMapping("/{id}/prior-authority")
  public ResponseEntity<CreatePriorAuthorityResponse> createPriorAuthority(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @PathVariable UUID id,
      @Valid @RequestBody CreatePriorAuthorityRequest request) {
    CreatePriorAuthorityCommand command = commandMapper.toCommand(id, request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{submissionId}")
            .buildAndExpand(command.submissionId())
            .toUri();
    CreatePriorAuthorityResponse body =
        new CreatePriorAuthorityResponse(
            command.submissionId(), command.occurredAt().atOffset(ZoneOffset.UTC));
    boolean projected = useCase.execute(command);
    return projected
        ? ResponseEntity.created(location).body(body)
        : ResponseEntity.accepted().location(location).body(body);
  }
}
