package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Creates an Application aggregate after parsing the request's application content. */
@Component
public class CreateApplicationCommandHandler {

  private final Repository<ApplyApplicationIdAggregate> applyApplicationIdRepository;
  private final ApplicationContentParser applicationContentParser;
  private final Clock clock;

  /** Creates the external handler using the system UTC clock. */
  @Autowired
  public CreateApplicationCommandHandler(
      Repository<ApplyApplicationIdAggregate> applyApplicationIdRepository,
      ApplicationContentParser applicationContentParser) {
    this(applyApplicationIdRepository, applicationContentParser, Clock.systemUTC());
  }

  CreateApplicationCommandHandler(
      Repository<ApplyApplicationIdAggregate> applyApplicationIdRepository,
      ApplicationContentParser applicationContentParser,
      Clock clock) {
    this.applyApplicationIdRepository = applyApplicationIdRepository;
    this.applicationContentParser = applicationContentParser;
    this.clock = clock;
  }

  /** Parses the command and creates only the Apply Application identifier claim. */
  @CommandHandler
  public UUID handle(CreateApplicationCommand command) throws Exception {
    ParsedAppContentDetails parsed = applicationContentParser.parse(command.applicationContent());
    ApplicationFinalisationDetails details = toFinalisationDetails(command, parsed);
    claimApplyApplicationId(command.applicationId(), details, resolveLeadApplicationId(parsed));
    return command.applicationId();
  }

  private void claimApplyApplicationId(
      UUID applicationId, ApplicationFinalisationDetails details, UUID leadApplicationId)
      throws Exception {
    try {
      applyApplicationIdRepository
          .load(details.applyApplicationId().toString())
          .execute(aggregate -> aggregate.claim(applicationId, details, leadApplicationId));
    } catch (AggregateNotFoundException exception) {
      applyApplicationIdRepository.newInstance(
          () -> new ApplyApplicationIdAggregate(applicationId, details, leadApplicationId));
    }
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

    linkedApplications.stream()
        .map(LinkedApplication::getAssociatedApplicationId)
        .filter(associatedId -> !associatedId.equals(parsed.applyApplicationId()))
        .filter(associatedId -> !associatedId.equals(leadApplyApplicationId))
        .distinct()
        .forEach(this::requireClaimedApplication);

    return requireClaimedApplication(leadApplyApplicationId);
  }

  private UUID requireClaimedApplication(UUID applyApplicationId) {
    try {
      UUID applicationId =
          applyApplicationIdRepository
              .load(applyApplicationId.toString())
              .invoke(ApplyApplicationIdAggregate::claimedApplicationId);
      if (applicationId != null) {
        return applicationId;
      }
    } catch (AggregateNotFoundException exception) {
      // Both absent and released claims are unavailable as linked applications.
    }
    throw new ResourceNotFoundException(
        "No linked application found with Apply Application ID: " + applyApplicationId);
  }

  private ApplicationFinalisationDetails toFinalisationDetails(
      CreateApplicationCommand command, ParsedAppContentDetails parsed) {
    return new ApplicationFinalisationDetails(
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
        Instant.now(clock));
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
