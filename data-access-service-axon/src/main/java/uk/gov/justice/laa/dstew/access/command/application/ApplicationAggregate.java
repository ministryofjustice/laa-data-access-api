package uk.gov.justice.laa.dstew.access.command.application;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.CreateLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ValidateApplicationExistsCommand;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Event-sourced consistency boundary for an Application and its owned child state. */
@Aggregate
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationAggregate {

  @AggregateIdentifier private UUID applicationId;
  private String status;
  private String laaReference;
  private ApplicationContent applicationContent;
  private List<ApplicationIndividual> individuals;
  private int schemaVersion;
  private String applicationType;
  private UUID applyApplicationId;
  private UUID leadApplicationId;
  private Instant submittedAt;
  private String officeCode;
  private Boolean usedDelegatedFunctions;
  private CategoryOfLaw categoryOfLaw;
  private MatterType matterType;
  private List<ApplicationProceeding> proceedings;
  private String serialisedRequest;

  /**
   * Creates or idempotently re-identifies an Application.
   *
   * <p>On the first command for this aggregate ID, parses the request and emits {@link
   * ApplicationCreatedEvent}. On an identical retry (same serialised request and schema version),
   * returns the existing ID with no events. On a conflicting retry (same ID, different payload or
   * schema version), throws {@link ApplicationCreationConflictException} with no events.
   *
   * <p>Linking to a lead application is initiated asynchronously by {@link
   * uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ApplicationGroupEventRouter}
   * after {@link ApplicationCreatedEvent} is processed; {@link ApplicationLinkedEvent} is no longer
   * emitted for new operations.
   */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  UUID handle(CreateApplicationCommand command, ApplicationCreationDetailsFactory factory) {
    if (applicationId != null) {
      if (serialisedRequest.equals(command.serialisedRequest())
          && schemaVersion == command.schemaVersion()) {
        return applicationId;
      }
      throw new ApplicationCreationConflictException(applicationId);
    }
    ApplicationCreationDetails details = factory.prepare(command);
    apply(applicationCreatedEvent(command.applicationId(), details));
    return applicationId;
  }

  /**
   * Validates the lead exists and records a group-formation request.
   *
   * <p>Because {@code ApplicationAggregate} uses {@code CREATE_IF_MISSING}, Axon will construct a
   * fresh (empty) aggregate if the lead does not exist. The {@code applicationId == null} guard
   * detects this and throws {@link ResourceNotFoundException} with no events applied.
   *
   * <p>The {@code groupId} is derived deterministically from the lead's {@code applicationId} via
   * {@link UUID#nameUUIDFromBytes}. This ensures all applications that reference the same lead
   * converge on the same {@link
   * uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate},
   * while remaining distinct from the lead's UUID (avoiding Axon event-stream collision — {@code
   * readEvents} queries by identifier only, regardless of aggregate type).
   */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  void handle(CreateLinkedApplicationGroupCommand command) {
    if (applicationId == null) {
      throw new ResourceNotFoundException(
          "No linked application found with Application ID: " + command.leadApplicationId());
    }
    UUID groupId =
        UUID.nameUUIDFromBytes(("linked-group:" + applicationId).getBytes(StandardCharsets.UTF_8));
    apply(
        new LinkedApplicationGroupRequested(
            groupId,
            applicationId, // leadApplicationId
            command.allMemberApplicationIds(),
            command.serialisedRequest(),
            command.occurredAt()));
  }

  /**
   * Proves that the targeted application exists.
   *
   * <p>Used by {@link
   * uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ApplicationGroupEventRouter} to
   * validate that other referenced associated applications are present before the {@link
   * uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate}
   * is initialised. If the aggregate does not exist, {@code CREATE_IF_MISSING} ghost-creates it and
   * the {@code applicationId == null} guard throws {@link ResourceNotFoundException}.
   */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  void handle(ValidateApplicationExistsCommand command) {
    if (applicationId == null) {
      throw new ResourceNotFoundException(
          "No linked application found with Application ID: " + command.applicationId());
    }
    // Application exists — no events, no state change.
  }

  @EventSourcingHandler
  void on(LinkedApplicationGroupRequested event) {
    // No state change on the lead aggregate — event is a domain record only.
  }

  @EventSourcingHandler
  void on(ApplicationCreatedEvent event) {
    applicationId = event.applicationId();
    status = event.status();
    laaReference = event.laaReference();
    applicationContent = event.applicationContent();
    individuals = List.copyOf(event.individuals());
    schemaVersion = event.schemaVersion();
    applicationType = event.applicationType();
    applyApplicationId = event.applyApplicationId();
    submittedAt = event.submittedAt();
    officeCode = event.officeCode();
    usedDelegatedFunctions = event.usedDelegatedFunctions();
    categoryOfLaw = event.categoryOfLaw();
    matterType = event.matterType();
    proceedings = List.copyOf(event.proceedings());
    serialisedRequest = event.serialisedRequest();
  }

  @EventSourcingHandler
  void on(ApplicationLinkedEvent event) {
    leadApplicationId = event.leadApplicationId();
  }

  private ApplicationCreatedEvent applicationCreatedEvent(
      UUID applicationId, ApplicationCreationDetails details) {
    return new ApplicationCreatedEvent(
        applicationId,
        details.status(),
        details.laaReference(),
        details.applicationContent(),
        details.individuals(),
        details.schemaVersion(),
        details.applicationType(),
        details.applyApplicationId(),
        details.submittedAt(),
        details.officeCode(),
        details.usedDelegatedFunctions(),
        details.categoryOfLaw(),
        details.matterType(),
        details.proceedings(),
        details.serialisedRequest(),
        details.occurredAt(),
        details.leadApplicationId());
  }

  protected ApplicationAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
