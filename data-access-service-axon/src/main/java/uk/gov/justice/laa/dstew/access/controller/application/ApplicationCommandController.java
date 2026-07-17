package uk.gov.justice.laa.dstew.access.controller.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.application.SubmitApplicationService;

/** HTTP command adapter for Application writes. */
@RestController
@RequestMapping("/api/v0/applications")
public class ApplicationCommandController {

  private final SubmitApplicationService submitApplicationService;

  public ApplicationCommandController(SubmitApplicationService submitApplicationService) {
    this.submitApplicationService = submitApplicationService;
  }

  /** Dispatches create synchronously and returns 201 Created with a Location header. */
  @PostMapping
  public ResponseEntity<Void> createApplication(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @RequestHeader(value = "X-Schema-Version", required = false, defaultValue = "1") @Min(1)
          int schemaVersion,
      @Valid @RequestBody ApplicationCreateRequest request) {
    UUID id = submitApplicationService.submit(request, schemaVersion);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    return ResponseEntity.created(location).build();
  }
}
