package uk.gov.justice.laa.dstew.access.command.application;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Event-sourced aggregate for application creation without a reservation saga. */
@Aggregate
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationAggregate {

  @AggregateIdentifier private UUID applyApplicationId;
  private String status;
  private String laaReference;
  private ApplicationContent applicationContent;
  private List<ApplicationIndividual> individuals;
  private int schemaVersion;
  private String applicationType;
  private Instant submittedAt;
  private String officeCode;
  private Boolean usedDelegatedFunctions;
  private CategoryOfLaw categoryOfLaw;
  private MatterType matterType;
  private List<ApplicationProceeding> proceedings;
  private final Set<UUID> priorAuthorityIds = new HashSet<>();

  /** Creates a Application, treating redelivery of the command as idempotent. */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  UUID handle(CreateApplicationCommand command, ApplicationContentParser parser, Clock clock) {
    if (applyApplicationId != null) {
      return applyApplicationId;
    }
    ParsedAppContentDetails parsed = parser.parse(command.applicationContent());
    apply(
        new ApplicationCreatedEvent(
            parsed.applyApplicationId(),
            command.status(),
            command.laaReference(),
            parsed.applicationContent(),
            toIndividuals(command.individuals()),
            command.schemaVersion(),
            command.applicationType(),
            parsed.submittedAt(),
            parsed.officeCode(),
            parsed.usedDelegatedFunctions(),
            parsed.categoryOfLaw(),
            parsed.matterType(),
            toProceedings(parsed.proceedings()),
            command.serialisedRequest(),
            Instant.now(clock)));
    return applyApplicationId;
  }

  /** Starts a new prior authority draft against this application. */
  @CommandHandler
  UUID handle(CreateDraftPriorAuthorityCommand command, Clock clock) {
    UUID priorAuthorityId = UUID.randomUUID();
    apply(
        new DraftPriorAuthorityCreatedEvent(
            applyApplicationId, priorAuthorityId, command.content(), Instant.now(clock)));
    return priorAuthorityId;
  }

  /** Updates an existing prior authority draft in place. */
  @CommandHandler
  void handle(UpdateDraftPriorAuthorityCommand command, Clock clock) {
    if (!priorAuthorityIds.contains(command.priorAuthorityId())) {
      throw new ResourceNotFoundException(
          "No prior authority draft found with ID: " + command.priorAuthorityId());
    }
    apply(
        new DraftPriorAuthorityUpdatedEvent(
            applyApplicationId, command.priorAuthorityId(), command.content(), Instant.now(clock)));
  }

  /** Starts a new draft application, treating redelivery of the command as idempotent. */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  UUID handle(CreateDraftApplicationCommand command, Clock clock) {
    if (applyApplicationId != null) {
      return applyApplicationId;
    }
    apply(
        new DraftApplicationCreatedEvent(
            command.draftApplicationId(), command.content(), Instant.now(clock)));
    return applyApplicationId;
  }

  /** Updates an existing draft application in place. */
  @CommandHandler
  void handle(UpdateDraftApplicationCommand command, Clock clock) {
    apply(
        new DraftApplicationUpdatedEvent(
            command.draftApplicationId(), command.content(), Instant.now(clock)));
  }

  @EventSourcingHandler
  void on(ApplicationCreatedEvent event) {
    applyApplicationId = event.applyApplicationId();
    status = event.status();
    laaReference = event.laaReference();
    applicationContent = event.applicationContent();
    individuals = List.copyOf(event.individuals());
    schemaVersion = event.schemaVersion();
    applicationType = event.applicationType();
    submittedAt = event.submittedAt();
    officeCode = event.officeCode();
    usedDelegatedFunctions = event.usedDelegatedFunctions();
    categoryOfLaw = event.categoryOfLaw();
    matterType = event.matterType();
    proceedings = List.copyOf(event.proceedings());
  }

  @EventSourcingHandler
  void on(DraftPriorAuthorityCreatedEvent event) {
    priorAuthorityIds.add(event.priorAuthorityId());
  }

  @EventSourcingHandler
  void on(DraftApplicationCreatedEvent event) {
    applyApplicationId = event.draftApplicationId();
  }

  private List<ApplicationIndividual> toIndividuals(List<ApplicationIndividual> individuals) {
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
                    proceeding.getId() == null ? null : proceeding.getId().toString(),
                    proceeding.getDescription(),
                    Boolean.TRUE.equals(proceeding.getLeadProceeding()),
                    proceeding))
        .toList();
  }

  protected ApplicationAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
