package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider;
import uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication;
import uk.gov.justice.laa.dstew.access.applicationcontent.Opponent;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;

/** Values needed by the Application aggregate to establish its initial state. */
public record ApplicationCreationDetails(
    String status,
    String laaReference,
    ApplicationClient client,
    ApplicationProvider provider,
    List<Opponent> opponents,
    List<LinkedApplication> allLinkedApplications,
    int schemaVersion,
    UUID applyApplicationId,
    Instant submittedAt,
    Boolean usedDelegatedFunctions,
    String categoryOfLaw,
    String matterType,
    List<Proceeding> proceedings,
    String serialisedRequest,
    Instant occurredAt,
    UUID leadApplicationId) {

  /** Normalises nullable collection fields to empty immutable lists. */
  public ApplicationCreationDetails {
    opponents = opponents == null ? List.of() : List.copyOf(opponents);
    allLinkedApplications =
        allLinkedApplications == null ? List.of() : List.copyOf(allLinkedApplications);
    proceedings = List.copyOf(proceedings);
  }
}
