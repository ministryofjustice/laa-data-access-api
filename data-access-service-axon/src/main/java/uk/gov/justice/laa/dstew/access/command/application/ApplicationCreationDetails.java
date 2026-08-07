package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;

/** Values needed by the Application aggregate to establish its initial state. */
public record ApplicationCreationDetails(
    String status,
    String laaReference,
    ApplicationContent applicationContent,
    List<ApplicationIndividual> individuals,
    int schemaVersion,
    UUID applyApplicationId,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    String categoryOfLaw,
    String matterType,
    List<ApplicationProceeding> proceedings,
    String serialisedRequest,
    Instant occurredAt,
    UUID leadApplicationId) {

  public ApplicationCreationDetails {
    individuals = List.copyOf(individuals);
    proceedings = List.copyOf(proceedings);
  }
}
