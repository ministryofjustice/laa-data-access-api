package uk.gov.justice.laa.dstew.access.command.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Command that creates or idempotently re-identifies an Application aggregate. The mapper currently
 * sets {@code applicationId} from the Apply content ID so the aggregate stream identifier equals
 * the Apply Application UUID.
 */
@Command(routingKey = "applicationId")
public record CreateApplicationCommand(
    @TargetEntityId UUID applicationId,
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    List<CreateApplicationIndividual> individuals,
    String serialisedRequest,
    int schemaVersion,
    String schemaName,
    String applicationType) {

  /** Returns the validated Apply identifier carried by the application content. */
  public UUID applyApplicationId() {
    return applicationId;
  }
}
