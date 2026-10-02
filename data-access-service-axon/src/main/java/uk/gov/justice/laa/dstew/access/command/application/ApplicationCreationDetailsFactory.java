package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

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
    return prepare(
        command.status(),
        command.laaReference(),
        command.applicationContent(),
        command.serialisedRequest(),
        command.schemaVersion(),
        command.potentialDuplicates());
  }

  /**
   * Parses draft content into creation details. Unlike {@link #prepare(CreateApplicationCommand)},
   * this does not require a schema name, since the caller (e.g. draft submission) has none to
   * supply.
   */
  public ApplicationCreationDetails prepare(
      String status,
      String laaReference,
      Map<String, Object> applicationContent,
      String serialisedRequest,
      int schemaVersion) {
    return prepare(
        status, laaReference, applicationContent, serialisedRequest, schemaVersion, List.of());
  }

  /**
   * Parses draft content into creation details, carrying forward the potential-duplicate references
   * recorded when the draft was created. Unlike {@link #prepare(CreateApplicationCommand)}, this
   * does not require a schema name, since the caller (e.g. draft submission) has none to supply.
   */
  public ApplicationCreationDetails prepare(
      String status,
      String laaReference,
      Map<String, Object> applicationContent,
      String serialisedRequest,
      int schemaVersion,
      List<PotentialDuplicate> potentialDuplicates) {
    ParsedAppContentDetails parsed = applicationContentParser.parse(applicationContent);
    return toCreationDetails(
        status, laaReference, serialisedRequest, schemaVersion, parsed, potentialDuplicates);
  }

  /**
   * Validates that the supplied application content can be parsed, without returning or otherwise
   * using the parsed result. Intended for call sites (e.g. draft creation) that only need to fail
   * fast on invalid content ahead of time, rather than consume the parsed details immediately.
   *
   * @throws ValidationException if the content fails semantic validation
   */
  public void validate(Map<String, Object> applicationContent) {
    applicationContentParser.parse(applicationContent);
  }

  private ApplicationCreationDetails toCreationDetails(
      String status,
      String laaReference,
      String serialisedRequest,
      int schemaVersion,
      ParsedAppContentDetails parsed,
      List<PotentialDuplicate> potentialDuplicates) {
    return new ApplicationCreationDetails(
        status,
        laaReference,
        parsed.client(),
        parsed.provider(),
        parsed.opponents(),
        schemaVersion,
        parsed.submittedAt(),
        parsed.usedDelegatedFunctions(),
        parsed.categoryOfLaw(),
        parsed.matterType(),
        parsed.proceedings(),
        serialisedRequest,
        Instant.now(clock),
        potentialDuplicates);
  }
}
