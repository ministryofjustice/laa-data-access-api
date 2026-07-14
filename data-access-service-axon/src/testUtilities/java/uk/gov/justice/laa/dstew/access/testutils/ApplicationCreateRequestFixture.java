package uk.gov.justice.laa.dstew.access.testutils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/** Builds valid API requests shared by fast and Postgres integration tests. */
public final class ApplicationCreateRequestFixture {

  private ApplicationCreateRequestFixture() {}

  /** Creates a valid request using the supplied Apply identifiers. */
  public static ApplicationCreateRequest validCreateApplicationRequest(
      UUID applyApplicationId, UUID applyProceedingId) {
    Map<String, Object> content =
        Map.of(
            "id",
            applyApplicationId.toString(),
            "submittedAt",
            "2026-07-14T12:30:00Z",
            "office",
            Map.of("code", "1A001B"),
            "proceedings",
            List.of(
                Map.of(
                    "id",
                    applyProceedingId.toString(),
                    "leadProceeding",
                    true,
                    "description",
                    "Care order",
                    "categoryOfLawEnum",
                    "FAMILY",
                    "matterTypeEnum",
                    "SPECIAL_CHILDREN_ACT",
                    "usedDelegatedFunctions",
                    false)));

    IndividualCreateRequest individual =
        IndividualCreateRequest.builder()
            .firstName("Ada")
            .lastName("Lovelace")
            .dateOfBirth(LocalDate.of(1815, 12, 10))
            .details(Map.of("preferredName", "Ada"))
            .type(IndividualType.CLIENT)
            .build();

    return ApplicationCreateRequest.builder()
        .applicationType(ApplicationType.APPLY)
        .status(ApplicationStatus.APPLICATION_SUBMITTED)
        .applicationContent(content)
        .laaReference("LAA-123")
        .individuals(List.of(individual))
        .build();
  }
}
