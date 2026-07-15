package uk.gov.justice.laa.dstew.access.command.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Command carrying the client-generated aggregate identifier and raw create request data. */
public record CreateApplicationCommand(
    UUID applicationId,
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    List<CreateApplicationIndividual> individuals,
    String serialisedRequest,
    int schemaVersion,
    String applicationType) {

  /** Returns the validated Apply identifier carried by the application content. */
  public UUID applyApplicationId() {
    return UUID.fromString(applicationContent.get("id").toString());
  }
}
