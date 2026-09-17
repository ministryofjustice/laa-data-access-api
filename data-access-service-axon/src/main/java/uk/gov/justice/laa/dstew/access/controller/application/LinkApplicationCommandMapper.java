package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkType;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;

/** Maps the generated link request to the command-side explicit-link contract. */
@Component
public class LinkApplicationCommandMapper {

  private final Clock clock;

  /** Creates the mapper with the system UTC clock. */
  @Autowired
  public LinkApplicationCommandMapper() {
    this(Clock.systemUTC());
  }

  LinkApplicationCommandMapper(Clock clock) {
    this.clock = clock;
  }

  /** Maps the source path identifier and target request identifier into a link command. */
  public LinkApplicationCommand toCommand(
      UUID sourceApplicationId, ApplicationLinkRequest request) {
    return new LinkApplicationCommand(
        sourceApplicationId,
        request.getApplicationId(),
        toDomain(request.getLinkType()),
        Instant.now(clock));
  }

  private LinkType toDomain(ApplicationLinkType linkType) {
    return switch (linkType) {
      case FAMILY -> LinkType.FAMILY;
    };
  }
}
