package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;

/**
 * Parses an incoming create command into {@link ApplicationCreationDetails}. This class is
 * responsible for parsing only; explicit linking is handled by the application-link endpoint after
 * creation.
 */
@Component
public class ApplicationCreationDetailsFactory {

  private final ApplicationContentParser applicationContentParser;
  private final Clock clock;

  /** Creates the factory using the system UTC clock. */
  @Autowired
  public ApplicationCreationDetailsFactory(ApplicationContentParser applicationContentParser) {
    this(applicationContentParser, Clock.systemUTC());
  }

  ApplicationCreationDetailsFactory(
      ApplicationContentParser applicationContentParser, Clock clock) {
    this.applicationContentParser = applicationContentParser;
    this.clock = clock;
  }

  /** Parses the command payload into creation details. */
  public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
    ParsedAppContentDetails parsed = applicationContentParser.parse(command.applicationContent());
    return toCreationDetails(command, parsed);
  }

  private ApplicationCreationDetails toCreationDetails(
      CreateApplicationCommand command, ParsedAppContentDetails parsed) {
    return new ApplicationCreationDetails(
        command.status(),
        command.laaReference(),
        parsed.client(),
        parsed.provider(),
        parsed.opponents(),
        command.schemaVersion(),
        parsed.submittedAt(),
        parsed.usedDelegatedFunctions(),
        parsed.categoryOfLaw(),
        parsed.matterType(),
        parsed.proceedings(),
        command.serialisedRequest(),
        Instant.now(clock),
        command.potentialDuplicates());
  }
}
