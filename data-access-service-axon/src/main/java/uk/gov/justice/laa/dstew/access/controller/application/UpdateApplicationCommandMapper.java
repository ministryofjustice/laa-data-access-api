package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;

/** Maps an Application PATCH request to its Axon command. */
@Component
public class UpdateApplicationCommandMapper {

  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** Creates the mapper with the system UTC clock. */
  @Autowired
  public UpdateApplicationCommandMapper(ObjectMapper objectMapper) {
    this(objectMapper, Clock.systemUTC());
  }

  UpdateApplicationCommandMapper(ObjectMapper objectMapper, Clock clock) {
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** Maps the path identifier and replacement content into a versioned update command. */
  public UpdateApplicationCommand toCommand(UUID id, ApplicationUpdateRequest request) {
    return new UpdateApplicationCommand(
        id,
        request.getStatus() == null ? null : request.getStatus().name(),
        request.getApplicationContent(),
        serialise(request),
        Instant.now(clock));
  }

  private String serialise(ApplicationUpdateRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise ApplicationUpdateRequest", exception);
    }
  }
}
