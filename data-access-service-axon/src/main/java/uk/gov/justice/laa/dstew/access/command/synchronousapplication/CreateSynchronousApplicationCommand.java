package uk.gov.justice.laa.dstew.access.command.synchronousapplication;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Command to create a new SynchronousApplication aggregate. */
public record CreateSynchronousApplicationCommand(
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    List<SynchronousApplicationIndividual> individuals,
    String serialisedRequest,
    int schemaVersion,
    String schemaName,
    String applicationType) {

  /** Returns the Apply Application identifier extracted from the content and used as aggregate id. */
  @TargetAggregateIdentifier
  public UUID applyApplicationId() {
    return UUID.fromString(applicationContent.get("id").toString());
  }
}

