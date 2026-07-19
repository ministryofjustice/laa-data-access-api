package uk.gov.justice.laa.dstew.access.command.application;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
import uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.AssignCaseworkerToApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.assignment.UnassignCaseworkerFromApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.CreateLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ValidateApplicationExistsCommand;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationGroupInvariantException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Event-sourced consistency boundary for an Application and its owned child state. */
@Aggregate
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationAggregate {

  @AggregateIdentifier private UUID applicationId;
  private boolean isAssociatedMember;
  private int schemaVersion;
  private String requestFingerprint;
  private long applicationDataVersion;
  private long applicationVersion;
  private UUID caseworkerId;

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
  UUID handle(
      CreateApplicationCommand command,
      ApplicationCreationDetailsFactory factory,
      ApplicationDataStore applicationDataStore) {
    if (applicationId != null) {
      if (requestFingerprint.equals(ApplicationDataStore.fingerprint(command.serialisedRequest()))
          && schemaVersion == command.schemaVersion()) {
        return applicationId;
      }
      throw new ApplicationCreationConflictException(applicationId);
    }
    ApplicationCreationDetails details = factory.prepare(command);
    if (details.leadApplicationId() != null
        && details.leadApplicationId().equals(command.applicationId())) {
      throw new ApplicationGroupInvariantException(
          "Application " + command.applicationId() + " cannot be its own lead");
    }
    long applicationDataVersion = 0L;
    String fingerprint =
        applicationDataStore.append(command.applicationId(), applicationDataVersion, details);
    apply(
        applicationCreatedEvent(
            command.applicationId(), applicationDataVersion, fingerprint, details));
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
    if (isAssociatedMember) {
      throw new ApplicationGroupInvariantException(
          "Application "
              + applicationId
              + " is already a member of another group and cannot be a lead");
    }
    UUID groupId =
        UUID.nameUUIDFromBytes(("linked-group:" + applicationId).getBytes(StandardCharsets.UTF_8));
    apply(
        new LinkedApplicationGroupRequested(
            groupId,
            applicationId, // leadApplicationId
            command.allMemberApplicationIds(),
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

  /** Validates and stores a decision as the next immutable application-data version. */
  @CommandHandler
  void handle(MakeApplicationDecisionCommand command, ApplicationDataStore applicationDataStore) {
    if (applicationId == null) {
      throw new ResourceNotFoundException(
          "No application found with id: " + command.applicationId());
    }
    if (command.expectedApplicationVersion() != applicationVersion) {
      throw new ApplicationVersionConflictException(
          command.applicationId(), command.expectedApplicationVersion());
    }
    validateDecision(command);

    var current = applicationDataStore.get(applicationId, applicationDataVersion);
    Set<UUID> linkedProceedingIds =
        current.proceedings().stream()
            .map(ApplicationProceeding::proceedingId)
            .collect(java.util.stream.Collectors.toSet());
    List<UUID> unknownProceedingIds =
        command.proceedings().stream()
            .map(MakeDecisionProceeding::proceedingId)
            .distinct()
            .filter(id -> !linkedProceedingIds.contains(id))
            .toList();
    if (!unknownProceedingIds.isEmpty()) {
      throw new ResourceNotFoundException(
          "No proceeding found with id: "
              + unknownProceedingIds.stream()
                  .map(UUID::toString)
                  .collect(java.util.stream.Collectors.joining(",")));
    }

    var meritsDecisions =
        new HashMap<>(
            current.meritsDecisions() == null ? java.util.Map.of() : current.meritsDecisions());
    command
        .proceedings()
        .forEach(
            proceeding ->
                meritsDecisions.put(
                    proceeding.proceedingId(),
                    new ApplicationMeritsDecision(
                        proceeding.decision(), proceeding.reason(), proceeding.justification())));
    long nextVersion = applicationDataVersion + 1;
    var updated =
        current.withDecision(
            command.overallDecision(),
            command.autoGranted(),
            meritsDecisions,
            "GRANTED".equals(command.overallDecision()) ? command.certificate() : null,
            command.serialisedRequest(),
            command.eventDescription());
    applicationDataStore.append(
        applicationId, nextVersion, updated, command.serialisedRequest(), command.occurredAt());
    apply(
        new ApplicationDecisionMadeEvent(
            applicationId,
            applicationVersion + 1,
            nextVersion,
            command.overallDecision(),
            command.autoGranted(),
            command.occurredAt()));
  }

  /** Assigns a caseworker and stores free-text audit data outside the event stream. */
  @CommandHandler
  void handle(
      AssignCaseworkerToApplicationCommand command, ApplicationDataStore applicationDataStore) {
    if (applicationId == null) {
      throw new ResourceNotFoundException(
          "No application found with id: " + command.applicationId());
    }
    var current = applicationDataStore.get(applicationId, applicationDataVersion);
    long nextDataVersion = applicationDataVersion + 1;
    applicationDataStore.append(
        applicationId,
        nextDataVersion,
        current.withAssignment(command.eventDescription()),
        command.serialisedRequest(),
        command.occurredAt());
    apply(
        new ApplicationAssignedToCaseworkerEvent(
            applicationId,
            applicationVersion + 1,
            nextDataVersion,
            command.caseworkerId(),
            command.occurredAt()));
  }

  /** Removes the assigned caseworker and stores free-text audit data outside the event stream. */
  @CommandHandler
  void handle(
      UnassignCaseworkerFromApplicationCommand command, ApplicationDataStore applicationDataStore) {
    if (applicationId == null) {
      throw new ResourceNotFoundException(
          "No application found with id: " + command.applicationId());
    }
    if (caseworkerId == null) {
      return;
    }
    var current = applicationDataStore.get(applicationId, applicationDataVersion);
    long nextDataVersion = applicationDataVersion + 1;
    applicationDataStore.append(
        applicationId,
        nextDataVersion,
        current.withAssignment(command.eventDescription()),
        command.serialisedRequest(),
        command.occurredAt());
    apply(
        new ApplicationUnassignedFromCaseworkerEvent(
            applicationId, applicationVersion + 1, nextDataVersion, command.occurredAt()));
  }

  private void validateDecision(MakeApplicationDecisionCommand command) {
    List<String> errors = new ArrayList<>();
    if (command.proceedings().isEmpty()) {
      errors.add("The Make Decision request must contain at least one proceeding");
    }
    if ("GRANTED".equals(command.overallDecision())
        && (command.certificate() == null || command.certificate().isEmpty())) {
      errors.add(
          "The Make Decision request must contain a certificate when overallDecision is GRANTED");
    }
    command.proceedings().stream()
        .filter(
            proceeding ->
                proceeding.justification() == null || proceeding.justification().isEmpty())
        .forEach(
            proceeding ->
                errors.add(
                    "The Make Decision request must contain a refusal justification for proceeding with id: "
                        + proceeding.proceedingId()));
    Set<UUID> ids = new HashSet<>();
    command.proceedings().stream()
        .map(MakeDecisionProceeding::proceedingId)
        .filter(id -> !ids.add(id))
        .forEach(id -> errors.add("Duplicate proceeding id: " + id));
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }

  @EventSourcingHandler
  void on(LinkedApplicationGroupRequested event) {
    // No state change on the lead aggregate — event is a domain record only.
  }

  @EventSourcingHandler
  void on(ApplicationCreatedEvent event) {
    applicationId = event.applicationId();
    isAssociatedMember = event.leadApplicationId() != null;
    schemaVersion = event.schemaVersion();
    requestFingerprint = event.requestFingerprint();
    applicationDataVersion = event.applicationDataVersion();
    applicationVersion = 0L;
  }

  @EventSourcingHandler
  void on(ApplicationDecisionMadeEvent event) {
    applicationVersion = event.applicationVersion();
    applicationDataVersion = event.applicationDataVersion();
  }

  @EventSourcingHandler
  void on(ApplicationAssignedToCaseworkerEvent event) {
    applicationVersion = event.applicationVersion();
    applicationDataVersion = event.applicationDataVersion();
    caseworkerId = event.caseworkerId();
  }

  @EventSourcingHandler
  void on(ApplicationUnassignedFromCaseworkerEvent event) {
    applicationVersion = event.applicationVersion();
    applicationDataVersion = event.applicationDataVersion();
    caseworkerId = null;
  }

  @EventSourcingHandler
  void on(ApplicationLinkedEvent event) {
    isAssociatedMember = true;
  }

  private ApplicationCreatedEvent applicationCreatedEvent(
      UUID applicationId,
      long applicationDataVersion,
      String fingerprint,
      ApplicationCreationDetails details) {
    return new ApplicationCreatedEvent(
        applicationId,
        applicationDataVersion,
        fingerprint,
        details.status(),
        details.schemaVersion(),
        details.applicationType(),
        details.applyApplicationId(),
        details.occurredAt(),
        details.leadApplicationId(),
        details.applicationContent() == null
                || details.applicationContent().getAllLinkedApplications() == null
            ? java.util.List.of()
            : details.applicationContent().getAllLinkedApplications().stream()
                .map(LinkedApplication::getAssociatedApplicationId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
  }

  protected ApplicationAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
