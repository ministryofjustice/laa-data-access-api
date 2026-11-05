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
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;

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
  @Deprecated
  public LinkApplicationCommand toCommand(
      UUID sourceApplicationId, ApplicationLinkRequest request) {
    return toCommand(sourceApplicationId, request, null);
  }

  /** Maps the source path identifier, target request identifier, and authenticated caller. */
  public LinkApplicationCommand toCommand(
      UUID sourceApplicationId, ApplicationLinkRequest request, AuthenticatedCaller caller) {
    return new LinkApplicationCommand(
        sourceApplicationId,
        request.getApplicationId(),
        toDomain(request.getLinkType()),
        request.getLinkedGroupVersion() == null
            ? null
            : LinkedGroupVersionTokens.decode(request.getLinkedGroupVersion()),
        Instant.now(clock),
        caller);
  }

  private LinkType toDomain(ApplicationLinkType linkType) {
    return switch (linkType) {
      case FAMILY -> LinkType.FAMILY;
    };
  }
}
