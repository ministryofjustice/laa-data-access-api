package uk.gov.justice.laa.dstew.access.command.application;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;

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
  private Instant submittedAt;
  private String officeCode;
  private Boolean usedDelegatedFunctions;
  private CategoryOfLaw categoryOfLaw;
  private MatterType matterType;
  private List<ApplicationProceeding> proceedings;

  ApplicationAggregate(ApplicationCreatedEvent event) {
    apply(event);
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
  }

  protected ApplicationAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
