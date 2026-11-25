package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;

/** Maps an Application PATCH request to its Axon command. */
@Component
public class UpdateApplicationCommandMapper {

  private final Clock clock;

  /** Creates the mapper with the system UTC clock. */
  @Autowired
  public UpdateApplicationCommandMapper() {
    this(Clock.systemUTC());
  }

  UpdateApplicationCommandMapper(Clock clock) {
    this.clock = clock;
  }

  /** Maps the path identifier and replacement content into a versioned update command. */
  public UpdateApplicationCommand toCommand(UUID id, ApplicationUpdateRequest request) {
    return new UpdateApplicationCommand(
        id,
        request.getStatus() == null ? null : request.getStatus().name(),
        request.getApplicationContent(),
        Instant.now(clock));
  }
}
