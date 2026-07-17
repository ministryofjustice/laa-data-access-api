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

  @AggregateMember private final List<PriorAuthority> priorAuthorities = new ArrayList<>();
  private boolean submitted;

  /**
   * Submits an application. The aggregate may already be alive as a draft (genesis {@code
   * ApplicationDraftedEvent}), in which case submission is a state transition; otherwise the
   * aggregate is created directly. Re-submission of an already-submitted application is idempotent.
   */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  UUID handle(CreateApplicationCommand command, Clock clock) {
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

  /** Starts a new draft application, treating redelivery of the command as idempotent. */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  UUID handle(CreateDraftApplicationCommand command, Clock clock) {
    if (applyApplicationId != null) {
      return applyApplicationId;
    }
    apply(
        new ApplicationDraftedEvent(
            command.draftApplicationId(), command.content(), Instant.now(clock)));
    return applyApplicationId;
  }

  /** Updates an existing draft application in place. */
  @CommandHandler
  void handle(UpdateDraftApplicationCommand command, Clock clock) {
    apply(
        new ApplicationDraftUpdatedEvent(
            command.draftApplicationId(), command.content(), Instant.now(clock)));
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
    applyApplicationId = event.draftApplicationId();
  }

  protected ApplicationAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
