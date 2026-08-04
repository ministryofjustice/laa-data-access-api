package uk.gov.justice.laa.dstew.access.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import uk.gov.justice.laa.dstew.access.query.application.FindStalledAssessmentsQuery;
import uk.gov.justice.laa.dstew.access.query.application.StalledAssessment;
import uk.gov.justice.laa.dstew.access.query.application.StalledAssessments;

@ExtendWith(OutputCaptureExtension.class)
class AssessmentReconciliationTest {

  @Test
  void givenOldUnassessedApplications_whenReconciliationRuns_thenReportsIdentifiersAndAge(
      CapturedOutput output) {
    QueryGateway queryGateway = org.mockito.Mockito.mock(QueryGateway.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    Instant now = Instant.parse("2026-08-04T10:00:00Z");
    UUID applicationId = UUID.fromString("8c9e6c2e-4f1a-4e3a-9c2b-1a2b3c4d5e6f");
    UUID applyApplicationId = UUID.fromString("7b8d5b1d-3e0f-4d2b-8b1a-0a1b2c3d4e5f");
    StalledAssessment stalled =
        new StalledAssessment(
            applicationId, applyApplicationId, 4L, now.minus(Duration.ofMinutes(45)));
    when(queryGateway.query(
            org.mockito.ArgumentMatchers.any(FindStalledAssessmentsQuery.class),
            org.mockito.ArgumentMatchers.eq(StalledAssessments.class)))
        .thenReturn(CompletableFuture.completedFuture(new StalledAssessments(List.of(stalled))));
    AssessmentReconciliation reconciliation =
        new AssessmentReconciliation(
            queryGateway, meterRegistry, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(15));

    reconciliation.run();

    ArgumentCaptor<FindStalledAssessmentsQuery> query =
        ArgumentCaptor.forClass(FindStalledAssessmentsQuery.class);
    verify(queryGateway)
        .query(query.capture(), org.mockito.ArgumentMatchers.eq(StalledAssessments.class));
    assertThat(query.getValue().submittedBefore()).isEqualTo(now.minus(Duration.ofMinutes(15)));
    assertThat(output)
        .contains(
            applicationId.toString(),
            applyApplicationId.toString(),
            "applicationVersion=4",
            "ageSeconds=2700");
    assertThat(meterRegistry.get("application.assessment.stalled").gauge().value()).isEqualTo(1);
    assertThat(meterRegistry.get("application.assessment.oldest.age").gauge().value())
        .isEqualTo(2700);
    assertThat(meterRegistry.get("application.assessment.reconciliation.fresh").gauge().value())
        .isEqualTo(1);
  }

  @Test
  void givenTheProjectionQueryFails_whenReconciliationRuns_thenMarksItsSignalsStale() {
    QueryGateway queryGateway = org.mockito.Mockito.mock(QueryGateway.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    when(queryGateway.query(
            org.mockito.ArgumentMatchers.any(FindStalledAssessmentsQuery.class),
            org.mockito.ArgumentMatchers.eq(StalledAssessments.class)))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("projection failed")));
    AssessmentReconciliation reconciliation =
        new AssessmentReconciliation(
            queryGateway,
            meterRegistry,
            Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC),
            Duration.ofMinutes(15));

    assertThatThrownBy(reconciliation::run).isInstanceOf(RuntimeException.class);

    assertThat(meterRegistry.get("application.assessment.reconciliation.fresh").gauge().value())
        .isZero();
  }
}
