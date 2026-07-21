package uk.gov.justice.laa.dstew.access.command.application;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.ForwardMatchingInstances;
import org.axonframework.spring.stereotype.Aggregate;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;
import uk.gov.justice.laa.dstew.access.exception.ConflictException;

/**
 * Event-sourced aggregate for application submission without a reservation saga.
 *
 * <p>The aggregate holds only non-PII structural state and opaque pointers: the submitted body
 * lives in a deletable table, referenced by {@code contentId} on the {@link
 * ApplicationSubmittedEvent}. Personal data never enters the aggregate or its event stream.
 */
@Aggregate
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationAggregate {

  @AggregateIdentifier private UUID applyApplicationId;
  private String status;
  private String laaReference;
  private int schemaVersion;
  private String applicationType;
  private Instant submittedAt;
  private String officeCode;
  private Boolean usedDelegatedFunctions;
  private CategoryOfLaw categoryOfLaw;
  private MatterType matterType;

  @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class)
  private final List<PriorAuthority> priorAuthorities = new ArrayList<>();

  private boolean submitted;
  private UUID assignedCaseworkerId;

  /**
   * Submits an existing draft application, transitioning it {@code DRAFT -> SUBMITTED}. The
   * aggregate must already be alive as a draft (genesis {@link ApplicationDraftedEvent}); there is
   * no one-shot create+submit. Re-submission of an already-submitted application is idempotent.
   */
  @CommandHandler
  UUID handle(SubmitApplicationCommand command, Clock clock) {
    if (submitted) {
      return applyApplicationId;
    }
    apply(
        new ApplicationSubmittedEvent(
            command.applyApplicationId(),
            command.contentId(),
            command.status(),
            command.laaReference(),
            command.schemaVersion(),
            command.applicationType(),
            command.submittedAt(),
            command.officeCode(),
            command.usedDelegatedFunctions(),
            command.categoryOfLaw(),
            command.matterType(),
            Instant.now(clock)));
    return command.applyApplicationId();
  }

  /**
   * Starts a new prior authority draft against this application. Prior authority is a
   * post-submission concern, so the application must already be {@code SUBMITTED}. The {@code
   * priorAuthorityId} is minted by the application layer, which has already written the draft body
   * to the deletable {@code prior_authority_drafts} table.
   */
  @CommandHandler
  void handle(CreatePriorAuthorityDraftCommand command, Clock clock) {
    if (!submitted) {
      throw new ConflictException(
          "Cannot add a prior authority to application "
              + applyApplicationId
              + "; the application is not submitted");
    }
    apply(
        new PriorAuthorityDraftedEvent(
            applyApplicationId, command.priorAuthorityId(), Instant.now(clock)));
  }

  /**
   * Creates or overwrites an application draft. Idempotent create-or-update: a fresh id begins the
   * draft (genesis {@link ApplicationDraftedEvent}); an existing draft is updated in place ({@link
   * ApplicationDraftUpdatedEvent}). A submitted application can no longer be edited as a draft.
   */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  void handle(PutDraftApplicationCommand command, Clock clock) {
    if (submitted) {
      throw new ConflictException(
          "Cannot edit application " + applyApplicationId + " as a draft; it is already submitted");
    }
    if (applyApplicationId == null) {
      apply(new ApplicationDraftedEvent(command.applyApplicationId(), Instant.now(clock)));
    } else {
      apply(new ApplicationDraftUpdatedEvent(command.applyApplicationId(), Instant.now(clock)));
    }
  }

  /**
   * Assigns a caseworker to this (submitted) application. Idempotent when the same caseworker is
   * assigned again; rejected with a conflict when a different caseworker already holds it.
   */
  @CommandHandler
  void handle(AssignApplicationCaseworkerCommand command, Clock clock) {
    requireSubmitted("assign a caseworker to");
    if (assignedCaseworkerId != null) {
      if (assignedCaseworkerId.equals(command.caseworkerId())) {
        return;
      }
      throw new ConflictException(
          "Application " + applyApplicationId + " is already assigned to a different caseworker");
    }
    apply(
        new CaseworkerAssignedEvent(
            applyApplicationId, null, command.caseworkerId(), Instant.now(clock)));
  }

  /**
   * Clears this application's caseworker assignment. Rejected with a conflict when it is not
   * currently assigned.
   */
  @CommandHandler
  void handle(UnassignApplicationCaseworkerCommand command, Clock clock) {
    if (assignedCaseworkerId == null) {
      throw new ConflictException(
          "Application " + applyApplicationId + " is not assigned to a caseworker");
    }
    apply(new CaseworkerUnassignedEvent(applyApplicationId, null, Instant.now(clock)));
  }

  @EventSourcingHandler
  void on(ApplicationSubmittedEvent event) {
    applyApplicationId = event.applyApplicationId();
    status = event.status();
    laaReference = event.laaReference();
    schemaVersion = event.schemaVersion();
    applicationType = event.applicationType();
    submittedAt = event.submittedAt();
    officeCode = event.officeCode();
    usedDelegatedFunctions = event.usedDelegatedFunctions();
    categoryOfLaw = event.categoryOfLaw();
    matterType = event.matterType();
    submitted = true;
  }

  @EventSourcingHandler
  void on(PriorAuthorityDraftedEvent event) {
    priorAuthorities.add(new PriorAuthority(event.priorAuthorityId()));
  }

  @EventSourcingHandler
  void on(ApplicationDraftedEvent event) {
    applyApplicationId = event.applyApplicationId();
  }

  @EventSourcingHandler
  void on(CaseworkerAssignedEvent event) {
    if (event.priorAuthorityId() == null) {
      assignedCaseworkerId = event.caseworkerId();
    }
  }

  @EventSourcingHandler
  void on(CaseworkerUnassignedEvent event) {
    if (event.priorAuthorityId() == null) {
      assignedCaseworkerId = null;
    }
  }

  private void requireSubmitted(String action) {
    if (!submitted) {
      throw new ConflictException(
          "Cannot " + action + " application " + applyApplicationId + "; it is not submitted");
    }
  }

  protected ApplicationAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
