package uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts;

import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntityV2;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntityV2;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntityV2;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntityV2;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepositoryV2;

/**
 * Shared test assertions for decision-related integration tests.
 *
 * <p>Navigates via ApplicationEntityV2 → ProceedingEntityV2 → MeritsDecisionEntityV2, matching the
 * new clean-architecture aggregate-root path.
 */
@Component
public class DecisionAsserts {

  @Autowired private ApplicationRepositoryV2 applicationRepositoryV2;

  /**
   * Fetches the saved {@link DecisionEntityV2} for the given application and asserts that it
   * matches the expected {@link MakeDecisionRequest}.
   *
   * <p>All repository and collection access happens within a single transaction so lazy collections
   * are initialised before the session closes.
   */
  @Transactional
  public void verifyDecisionSavedCorrectly(
      UUID applicationId, MakeDecisionRequest expectedMakeDecisionRequest) {

    ApplicationEntityV2 app = applicationRepositoryV2.findById(applicationId).orElseThrow();

    DecisionEntityV2 savedDecision = app.getDecision();
    Assertions.assertThat(savedDecision).isNotNull();
    Assertions.assertThat(savedDecision.getOverallDecision().name())
        .isEqualTo(expectedMakeDecisionRequest.getOverallDecision().name());
    Assertions.assertThat(savedDecision.getModifiedAt()).isNotNull();

    // Verify merits decisions via the proceedings they belong to
    List<MakeDecisionProceedingRequest> expectedProceedings =
        expectedMakeDecisionRequest.getProceedings();
    for (MakeDecisionProceedingRequest expected : expectedProceedings) {
      ProceedingEntityV2 proceeding =
          app.getProceedings().stream()
              .filter(p -> p.getId().equals(expected.getProceedingId()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new AssertionError(
                          "No proceeding found with id: " + expected.getProceedingId()));

      MeritsDecisionEntityV2 merits = proceeding.getMeritsDecision();
      Assertions.assertThat(merits).isNotNull();
      Assertions.assertThat(merits.getDecision().name())
          .isEqualTo(expected.getMeritsDecision().getDecision().name());
      Assertions.assertThat(merits.getReason()).isEqualTo(expected.getMeritsDecision().getReason());
      Assertions.assertThat(merits.getJustification())
          .isEqualTo(expected.getMeritsDecision().getJustification());
      Assertions.assertThat(merits.getModifiedAt()).isNotNull();
    }
  }
}
