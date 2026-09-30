package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MakeApplicationLeadCommand;
import uk.gov.justice.laa.dstew.access.model.LinkedGroupChangeRequest;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Maps the make-lead request to its command-side contract. */
@Component
public class MakeApplicationLeadCommandMapper {

  private final Clock clock;

  /** Creates the mapper with the system UTC clock. */
  @Autowired
  public MakeApplicationLeadCommandMapper() {
    this(Clock.systemUTC());
  }

  MakeApplicationLeadCommandMapper(Clock clock) {
    this.clock = clock;
  }

  /** Maps a request for the supplied Application identifier. */
  public MakeApplicationLeadCommand toCommand(
      UUID applicationId, LinkedGroupChangeRequest request) {
    if (request.getLinkedGroupVersion() == null) {
      throw new ValidationException(List.of("linkedGroupVersion: must not be null"));
    }
    return new MakeApplicationLeadCommand(
        applicationId, request.getLinkedGroupVersion(), Instant.now(clock));
  }
}
