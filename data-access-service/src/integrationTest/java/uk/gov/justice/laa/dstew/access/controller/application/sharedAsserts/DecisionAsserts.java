package uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts;

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

/**
 * Shared test assertions for decision-related integration tests.
 *
 * <p>Methods are {@link Transactional} so that the JPA session remains open while lazy collections
 * are traversed, avoiding {@link org.hibernate.LazyInitializationException}.
 */
@Component
public class DecisionAsserts {

  @Autowired private ApplicationRepository applicationRepository;

  /**
   * Fetches the saved {@link DecisionEntity} for the given application and asserts that it matches
   * the expected {@link MakeDecisionRequest}. Merits decisions are now read from proceedings.
   */
  @Transactional
  public void verifyDecisionSavedCorrectly(
      UUID applicationId, MakeDecisionRequest expectedMakeDecisionRequest) {
    ApplicationEntity app = applicationRepository.findById(applicationId).orElseThrow();
    DecisionEntity savedDecision = app.getDecision();

    MakeDecisionRequest actual =
        MakeDecisionRequest.builder()
            .overallDecision(savedDecision.getOverallDecision())
            .eventHistory(expectedMakeDecisionRequest.getEventHistory())
            .proceedings(
                app.getProceedings().stream()
                    .filter(p -> p.getMeritsDecision() != null)
                    .map(
                        p ->
                            MakeDecisionProceedingRequest.builder()
                                .proceedingId(p.getId())
                                .meritsDecision(
                                    MeritsDecisionDetailsRequest.builder()
                                        .decision(p.getMeritsDecision().getDecision())
                                        .reason(p.getMeritsDecision().getReason())
                                        .justification(p.getMeritsDecision().getJustification())
                                        .build())
                                .build())
                    .toList())
            .autoGranted(app.getIsAutoGranted())
            .build();

    Assertions.assertThat(actual)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .ignoringFields("certificate", "applicationVersion")
        .isEqualTo(expectedMakeDecisionRequest);

    Assertions.assertThat(savedDecision.getModifiedAt()).isNotNull();
    app.getProceedings().stream()
        .filter(p -> p.getMeritsDecision() != null)
        .forEach(p -> Assertions.assertThat(p.getMeritsDecision().getModifiedAt()).isNotNull());
  }
}
