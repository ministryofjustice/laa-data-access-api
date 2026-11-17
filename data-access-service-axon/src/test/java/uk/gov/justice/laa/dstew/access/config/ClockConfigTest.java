package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.eventhandling.processing.errorhandling.ErrorContext;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import uk.gov.justice.laa.dstew.access.observability.AssessmentReconciliation;
import uk.gov.justice.laa.dstew.access.observability.ErrorReporter;
import uk.gov.justice.laa.dstew.access.observability.ReportingErrorHandler;
import uk.gov.justice.laa.dstew.access.query.application.FindStalledAssessmentsQuery;
import uk.gov.justice.laa.dstew.access.query.application.StalledAssessments;

@ExtendWith(MockitoExtension.class)
class ClockConfigTest {

  @Mock private ErrorReporter errorReporter;
  @Mock private QueryGateway queryGateway;

  @Test
  void providesOneSharedUtcClock() {
    try (var context = new AnnotationConfigApplicationContext(ClockConfig.class)) {
      assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
      Clock clock = context.getBean(Clock.class);
      assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
      assertThat(context.getBean(Clock.class)).isSameAs(clock);
    }
  }

  @Test
  void wiresObservabilityWithFixedClockAndConfiguredDurations() {
    Clock fixedClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    when(queryGateway.query(any(FindStalledAssessmentsQuery.class), eq(StalledAssessments.class)))
        .thenReturn(CompletableFuture.completedFuture(new StalledAssessments(List.of())));

    new ApplicationContextRunner()
        .withInitializer(
            context ->
                context
                    .getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withBean(Clock.class, () -> fixedClock)
        .withBean(ErrorReporter.class, () -> errorReporter)
        .withBean(QueryGateway.class, () -> queryGateway)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withUserConfiguration(
            AxonEventProcessingConfig.class,
            ReportingErrorHandler.class,
            AssessmentReconciliation.class)
        .withPropertyValues(
            "application.assessment.reconciliation.threshold=2m",
            "application.error-reporting.axon-projection-cooldown=1h")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(Clock.class)).isSameAs(fixedClock);
              context.getBean(AssessmentReconciliation.class).run();
              ArgumentCaptor<FindStalledAssessmentsQuery> query =
                  ArgumentCaptor.forClass(FindStalledAssessmentsQuery.class);
              verify(queryGateway).query(query.capture(), eq(StalledAssessments.class));
              assertThat(query.getValue().submittedBefore())
                  .isEqualTo(fixedClock.instant().minus(Duration.ofMinutes(2)));

              RuntimeException failure = new IllegalStateException("projection failed");
              ErrorContext errorContext =
                  new ErrorContext("application-projection", failure, List.of(), Context.empty());
              ReportingErrorHandler handler = context.getBean(ReportingErrorHandler.class);
              for (int attempt = 0; attempt < 2; attempt++) {
                assertThatThrownBy(() -> handler.handleError(errorContext)).isSameAs(failure);
              }
              verify(errorReporter, times(1)).report(anyString(), same(failure));
            });
  }
}
