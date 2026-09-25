package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/** Test builder for creating PotentialDuplicate objects in tests. */
public class PotentialDuplicateTestBuilder {

  private String laaReference = "LAA-TEST-123";
  private UUID applicationId = UUID.randomUUID();
  private String legacyReference = "LEGACY-TEST-999";

  public static PotentialDuplicateTestBuilder builder() {
    return new PotentialDuplicateTestBuilder();
  }

  public PotentialDuplicateTestBuilder withLaaReference(String laaReference) {
    this.laaReference = laaReference;
    return this;
  }

  public PotentialDuplicateTestBuilder withApplicationId(UUID applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  public PotentialDuplicateTestBuilder withLegacyReference(String legacyReference) {
    this.legacyReference = legacyReference;
    return this;
  }

  public PotentialDuplicate build() {
    PotentialDuplicate duplicate = new PotentialDuplicate();
    duplicate.setLaaReference(laaReference);
    duplicate.setApplicationId(applicationId);
    duplicate.setLegacyReference(legacyReference);
    return duplicate;
  }
}
