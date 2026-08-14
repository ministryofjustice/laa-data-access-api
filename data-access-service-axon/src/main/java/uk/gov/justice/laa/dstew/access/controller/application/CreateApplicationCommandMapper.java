package uk.gov.justice.laa.dstew.access.controller.application;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;

/** Maps the generated HTTP request model to the Axon create command. */
@Component
public class CreateApplicationCommandMapper {

  private final ObjectMapper objectMapper;

  public CreateApplicationCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Creates a command whose aggregate identifier is taken from the top-level request ID. */
  public CreateApplicationCommand toCommand(ApplicationCreateRequest request, int schemaVersion) {
    return new CreateApplicationCommand(
        request.getId(),
        request.getStatus() == null ? null : request.getStatus().name(),
        request.getLaaReference(),
        request.getApplicationContent(),
        serialise(request),
        schemaVersion,
        "BaseCivilApplication.json");
  }

  private String serialise(ApplicationCreateRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise ApplicationCreateRequest", exception);
    }
  }
}
