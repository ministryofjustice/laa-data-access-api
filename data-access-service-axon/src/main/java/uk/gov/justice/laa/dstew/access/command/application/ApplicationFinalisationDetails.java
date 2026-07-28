package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;

/** Values needed by the Application aggregate to establish its initial state. */
public record ApplicationFinalisationDetails(
    String status,
    String laaReference,
    ApplicationContent applicationContent,
    List<ApplicationIndividual> individuals,
    int schemaVersion,
    String applicationType,
    UUID applyApplicationId,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    List<ApplicationProceeding> proceedings,
    String serialisedRequest,
    Instant occurredAt) {

  public ApplicationFinalisationDetails {
    individuals = List.copyOf(individuals);
    proceedings = List.copyOf(proceedings);
  }
}
