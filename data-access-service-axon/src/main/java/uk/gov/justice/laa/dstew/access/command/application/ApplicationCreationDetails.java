package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.List;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider;
import uk.gov.justice.laa.dstew.access.applicationcontent.Opponent;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;

/** Values needed by the Application aggregate to establish its initial state. */
public record ApplicationCreationDetails(
    String status,
    String laaReference,
    ApplicationClient client,
    ApplicationProvider provider,
    List<Opponent> opponents,
    int schemaVersion,
    Instant submittedAt,
    Boolean usedDelegatedFunctions,
    String categoryOfLaw,
    String matterType,
    List<Proceeding> proceedings,
    String serialisedRequest,
    Instant occurredAt) {

  /** Normalises nullable collection fields to empty immutable lists. */
  public ApplicationCreationDetails {
    opponents = opponents == null ? List.of() : List.copyOf(opponents);
    proceedings = List.copyOf(proceedings);
  }
}
