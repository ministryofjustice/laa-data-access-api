package uk.gov.justice.laa.dstew.access.query.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider;
import uk.gov.justice.laa.dstew.access.applicationcontent.Opponent;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/** Detached read model of a submitted Application, built from its aggregate. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ExcludeFromGeneratedCodeCoverage
public class ApplicationReadModel {

  private UUID applicationId;

  private String status;

  private long applicationVersion;

  private String laaReference;

  private ApplicationClient client;

  private ApplicationProvider provider;

  private String officeCode;

  private List<Opponent> opponents;

  private int schemaVersion;

  private UUID leadApplicationId;

  private UUID linkedGroupId;

  private UUID caseworkerId;

  private Instant submittedAt;

  private Boolean usedDelegatedFunctions;

  private String categoryOfLaw;

  private String matterType;

  private String categoryOfLawCode;

  private String matterTypeCode;

  private List<Proceeding> proceedings;

  private String decisionStatus;

  private AutoGrantedState autoGranted;

  private Map<UUID, ApplicationMeritsDecision> meritsDecisions;

  private Map<String, Object> certificate;

  @Builder.Default private List<DocumentMetadata> uploadedDocuments = List.of();

  private Map<UUID, String> documentFilenames;

  private List<PotentialDuplicate> potentialDuplicates;

  private Instant createdAt;

  private Instant modifiedAt;

  /** Returns the office code, falling back to the provider in the content. */
  public String getOfficeCode() {
    return officeCode != null ? officeCode : provider == null ? null : provider.getOfficeCode();
  }
}
