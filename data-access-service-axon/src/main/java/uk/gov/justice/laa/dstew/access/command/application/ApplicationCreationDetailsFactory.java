package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;

/**
 * Parses an incoming create command into {@link ApplicationCreationDetails}. This class is
 * responsible for parsing only; link validation (lead-application existence) is the responsibility
 * of {@code LinkedApplicationGroupAggregate} via {@code CreateLinkedApplicationGroupCommand}.
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

  /** Parses the command payload and extracts link metadata into creation details. */
  public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
    ParsedAppContentDetails parsed = applicationContentParser.parse(command.applicationContent());
    UUID leadApplicationId = extractLeadApplicationId(parsed);
    return toCreationDetails(command, parsed, leadApplicationId);
  }

  /**
   * Extracts the lead application ID from the first linked-application entry, if present.
   *
   * <p>No repository lookup is performed; validation that the lead exists is delegated to {@code
   * CreateLinkedApplicationGroupCommand} targeting the lead aggregate.
   */
  private UUID extractLeadApplicationId(ParsedAppContentDetails parsed) {
    List<LinkedApplication> linkedApplications = parsed.allLinkedApplications();
    if (linkedApplications == null || linkedApplications.isEmpty()) {
      return null;
    }
    return linkedApplications.getFirst().getLeadApplicationId();
  }

  private ApplicationCreationDetails toCreationDetails(
      CreateApplicationCommand command, ParsedAppContentDetails parsed, UUID leadApplicationId) {
    return new ApplicationCreationDetails(
        command.status(),
        command.laaReference(),
        parsed.applicationContent(),
        toIndividuals(command.individuals()),
        command.schemaVersion(),
        command.applicationType(),
        parsed.applyApplicationId(),
        parsed.submittedAt(),
        parsed.officeCode(),
        parsed.usedDelegatedFunctions(),
        parsed.categoryOfLaw(),
        parsed.matterType(),
        toProceedings(parsed.proceedings()),
        command.serialisedRequest(),
        Instant.now(clock),
        leadApplicationId);
  }

  private List<ApplicationIndividual> toIndividuals(List<CreateApplicationIndividual> individuals) {
    return individuals.stream()
        .map(
            individual ->
                new ApplicationIndividual(
                    UUID.randomUUID(),
                    individual.firstName(),
                    individual.lastName(),
                    individual.dateOfBirth(),
                    individual.individualContent(),
                    individual.type()))
        .toList();
  }

  private List<ApplicationProceeding> toProceedings(List<Proceeding> proceedings) {
    return proceedings.stream()
        .map(
            proceeding ->
                new ApplicationProceeding(
                    UUID.randomUUID(),
                    proceeding.getId(),
                    proceeding.getDescription(),
                    Boolean.TRUE.equals(proceeding.getLeadProceeding()),
                    proceeding))
        .toList();
  }
}
