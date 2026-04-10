package uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts;

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

/**
 * Shared test assertions for decision-related integration tests.
 *
 * <p>Methods are {@link Transactional} so that the JPA session remains open while lazy collections
 * (e.g. {@code DecisionEntity.meritsDecisions}) are traversed, avoiding {@link
 * org.hibernate.LazyInitializationException}.
 */
@Component
public class DecisionAsserts {

  @Autowired private ApplicationRepository applicationRepository;

  /**
   * Fetches the saved {@link DecisionEntity} for the given application and asserts that it matches
   * the expected {@link MakeDecisionRequest}.
   *
   * <p>All repository and collection access happens within a single transaction so lazy collections
   * are initialised before the session closes.
   */
  @Transactional
  public void verifyDecisionSavedCorrectly(
      UUID applicationId, MakeDecisionRequest expectedMakeDecisionRequest) {
    ApplicationEntity updatedApplicationEntity =
        applicationRepository.findById(applicationId).orElseThrow();

    DecisionEntity savedDecision = updatedApplicationEntity.getDecision();

    MakeDecisionRequest actual =
        mapToMakeDecisionRequest(
            savedDecision, updatedApplicationEntity, expectedMakeDecisionRequest.getEventHistory());

    Assertions.assertThat(actual)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .ignoringFields("certificate", "applicationVersion")
        .isEqualTo(expectedMakeDecisionRequest);

    Assertions.assertThat(savedDecision.getModifiedAt()).isNotNull();
    Assertions.assertThat(savedDecision.getMeritsDecisions())
        .allSatisfy(merits -> Assertions.assertThat(merits.getModifiedAt()).isNotNull());
  }

  // ── Mapping helpers ───────────────────────────────────────────────────────

  private static MakeDecisionRequest mapToMakeDecisionRequest(
      DecisionEntity decisionEntity,
      ApplicationEntity applicationEntity,
      EventHistoryRequest eventHistoryRequest) {
    if (decisionEntity == null) return null;
    return MakeDecisionRequest.builder()
        .overallDecision(decisionEntity.getOverallDecision())
        .eventHistory(eventHistoryRequest)
        .proceedings(
            decisionEntity.getMeritsDecisions().stream()
                .map(DecisionAsserts::mapToProceedingDetails)
                .toList())
        .autoGranted(applicationEntity.getIsAutoGranted())
        .build();
  }

  private static MakeDecisionProceedingRequest mapToProceedingDetails(
      MeritsDecisionEntity meritsDecisionEntity) {
    if (meritsDecisionEntity == null) return null;
    return MakeDecisionProceedingRequest.builder()
        .proceedingId(meritsDecisionEntity.getProceeding().getId())
        .meritsDecision(mapToMeritsDecisionDetails(meritsDecisionEntity))
        .build();
  }

  private static MeritsDecisionDetailsRequest mapToMeritsDecisionDetails(
      MeritsDecisionEntity entity) {
    if (entity == null) return null;
    return MeritsDecisionDetailsRequest.builder()
        .decision(entity.getDecision())
        .reason(entity.getReason())
        .justification(entity.getJustification())
        .build();
  }
}
