package uk.gov.justice.laa.dstew.access.command.application;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;
import uk.gov.justice.laa.dstew.access.validation.DuplicateApplyApplicationIdException;

/** Write-side index that gives each Apply Application identifier one owner. */
@Aggregate
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplyApplicationIdAggregate {

  @AggregateIdentifier private UUID applyApplicationId;
  private UUID applicationId;
  private boolean claimed;

  /** Creates the claim and its event stream. */
  ApplyApplicationIdAggregate(
      UUID applicationId,
      ApplicationFinalisationDetails applicationFinalisationDetails,
      UUID leadApplicationId) {
    apply(claimedEvent(applicationId, applicationFinalisationDetails, leadApplicationId));
  }

  void claim(
      UUID applicationId,
      ApplicationFinalisationDetails applicationFinalisationDetails,
      UUID leadApplicationId) {
    if (claimed) {
      throw new DuplicateApplyApplicationIdException(applyApplicationId);
    }
    apply(claimedEvent(applicationId, applicationFinalisationDetails, leadApplicationId));
  }

  /** Returns the owning Application only while this identifier is actively claimed. */
  UUID claimedApplicationId() {
    return claimed ? applicationId : null;
  }

  /** Releases this claim only when it still belongs to the failed Application creation. */
  @CommandHandler
  void handle(ReleaseApplyApplicationIdCommand command) {
    if (claimed && applicationId.equals(command.applicationId())) {
      apply(new ApplyApplicationIdReleasedEvent(applyApplicationId, applicationId));
    }
  }

  @EventSourcingHandler
  void on(ApplyApplicationIdClaimedEvent event) {
    applyApplicationId = event.applyApplicationId();
    applicationId = event.applicationId();
    claimed = true;
  }

  @EventSourcingHandler
  void on(ApplyApplicationIdReleasedEvent event) {
    claimed = false;
  }

  private ApplyApplicationIdClaimedEvent claimedEvent(
      UUID applicationId,
      ApplicationFinalisationDetails applicationFinalisationDetails,
      UUID leadApplicationId) {
    return new ApplyApplicationIdClaimedEvent(
        applicationFinalisationDetails.applyApplicationId(),
        applicationId,
        applicationFinalisationDetails,
        leadApplicationId);
  }

  protected ApplyApplicationIdAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
