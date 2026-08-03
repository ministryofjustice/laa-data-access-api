package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.model.ReadyApplicationRequest;

/** Maps the manual-readiness HTTP contract to its Axon command. */
@Component
public class ReadyApplicationCommandMapper {

  private final ObjectMapper objectMapper;

  public ReadyApplicationCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps a request for the supplied Application identifier. */
  public MarkApplicationReadyCommand toCommand(
      UUID applicationId, ReadyApplicationRequest request) {
    return new MarkApplicationReadyCommand(
        applicationId, request.getApplicationVersion(), serialise(request), Instant.now());
  }

  private String serialise(ReadyApplicationRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise ReadyApplicationRequest", exception);
    }
  }
}
