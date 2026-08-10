package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Maps the generated HTTP request model to the Axon create command. */
@Component
public class CreateApplicationCommandMapper {

  private final ObjectMapper objectMapper;

  public CreateApplicationCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Creates a command whose aggregate identifier is extracted from the Apply content ID. */
  public CreateApplicationCommand toCommand(ApplicationCreateRequest request, int schemaVersion) {
    return new CreateApplicationCommand(
        extractApplicationId(request.getApplicationContent()),
        request.getStatus() == null ? null : request.getStatus().name(),
        request.getLaaReference(),
        request.getApplicationContent(),
        serialise(request),
        schemaVersion,
        "ApplyApplication.json");
  }

  private UUID extractApplicationId(Map<String, Object> applicationContent) {
    if (applicationContent == null || !applicationContent.containsKey("id")) {
      throw new ValidationException(
          List.of("applicationContent.id: must be present and a valid UUID"));
    }
    Object idObj = applicationContent.get("id");
    if (idObj == null) {
      throw new ValidationException(
          List.of("applicationContent.id: must be present and a valid UUID"));
    }
    String idValue = idObj.toString();
    try {
      return UUID.fromString(idValue);
    } catch (IllegalArgumentException e) {
      throw new ValidationException(
          List.of("applicationContent.id: must be a valid UUID, got: " + idValue));
    }
  }

  private String serialise(ApplicationCreateRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise ApplicationCreateRequest", exception);
    }
  }
}
