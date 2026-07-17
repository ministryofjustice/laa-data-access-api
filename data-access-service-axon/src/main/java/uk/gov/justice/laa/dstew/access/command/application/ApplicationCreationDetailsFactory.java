package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Prepares the creation details for a new Application aggregate from a create command. */
@Component
public class ApplicationCreationDetailsFactory {

  private final Repository<ApplicationAggregate> applicationRepository;
  private final ApplicationContentParser applicationContentParser;
  private final Clock clock;

  /** Creates the factory using the system UTC clock. */
  @Autowired
  public ApplicationCreationDetailsFactory(
      Repository<ApplicationAggregate> applicationRepository,
      ApplicationContentParser applicationContentParser) {
    this(applicationRepository, applicationContentParser, Clock.systemUTC());
  }

  ApplicationCreationDetailsFactory(
      Repository<ApplicationAggregate> applicationRepository,
      ApplicationContentParser applicationContentParser,
      Clock clock) {
    this.applicationRepository = applicationRepository;
    this.applicationContentParser = applicationContentParser;
    this.clock = clock;
  }

  /** Parses the command and resolves linked application references into creation details. */
  public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
    ParsedAppContentDetails parsed = applicationContentParser.parse(command.applicationContent());
    UUID leadApplicationId = resolveLeadApplicationId(parsed);
    return toCreationDetails(command, parsed, leadApplicationId);
  }

  private UUID resolveLeadApplicationId(ParsedAppContentDetails parsed) {
    List<LinkedApplication> linkedApplications = parsed.allLinkedApplications();
    if (linkedApplications == null || linkedApplications.isEmpty()) {
      return null;
    }

    UUID leadApplyApplicationId = linkedApplications.getFirst().getLeadApplicationId();
    if (leadApplyApplicationId == null) {
      return null;
    }

    // Associated applications: self (current apply ID) is already excluded by the filter below;
    // a new aggregate has no event stream yet so the self-reference is skipped.
    linkedApplications.stream()
        .map(LinkedApplication::getAssociatedApplicationId)
        .filter(associatedId -> !associatedId.equals(parsed.applyApplicationId()))
        .filter(associatedId -> !associatedId.equals(leadApplyApplicationId))
        .distinct()
        .forEach(this::requireExistingApplicationStream);

    // Lead must always be an existing stream; self-referential lead is not permitted.
    return requireExistingApplicationStream(leadApplyApplicationId);
  }

  private UUID requireExistingApplicationStream(UUID applicationId) {
    try {
      Boolean exists = applicationRepository.load(applicationId.toString()).invoke(a -> a != null);
      if (Boolean.TRUE.equals(exists)) {
        return applicationId;
      }
    } catch (AggregateNotFoundException exception) {
      // Application stream does not exist — fall through to throw.
    }
    throw new ResourceNotFoundException(
        "No linked application found with Application ID: " + applicationId);
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
