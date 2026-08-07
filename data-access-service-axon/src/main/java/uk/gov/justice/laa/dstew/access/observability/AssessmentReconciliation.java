package uk.gov.justice.laa.dstew.access.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.application.FindStalledAssessmentsQuery;
import uk.gov.justice.laa.dstew.access.query.application.StalledAssessment;
import uk.gov.justice.laa.dstew.access.query.application.StalledAssessments;

/** Detects and reports old submitted Applications without mutating or republishing them. */
@Component
public class AssessmentReconciliation {

  private static final Logger LOG = LoggerFactory.getLogger(AssessmentReconciliation.class);

  private final QueryGateway queryGateway;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final Duration threshold;
  private final AtomicLong stalledCount = new AtomicLong();
  private final AtomicLong oldestAgeSeconds = new AtomicLong();
  private final AtomicLong fresh = new AtomicLong();

  /** Creates reconciliation with the configured age threshold. */
  @Autowired
  public AssessmentReconciliation(
      QueryGateway queryGateway,
      MeterRegistry meterRegistry,
      @Value("${application.assessment.reconciliation.threshold:15m}") Duration threshold) {
    this(queryGateway, meterRegistry, Clock.systemUTC(), threshold);
  }

  AssessmentReconciliation(
      QueryGateway queryGateway, MeterRegistry meterRegistry, Clock clock, Duration threshold) {
    this.queryGateway = queryGateway;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
    this.threshold = threshold;
    Gauge.builder("application.assessment.stalled", stalledCount, AtomicLong::get)
        .description("Applications awaiting automatic assessment beyond the threshold")
        .register(meterRegistry);
    Gauge.builder("application.assessment.oldest.age", oldestAgeSeconds, AtomicLong::get)
        .baseUnit("seconds")
        .description("Age of the oldest Application awaiting automatic assessment")
        .register(meterRegistry);
    Gauge.builder("application.assessment.reconciliation.fresh", fresh, AtomicLong::get)
        .description("Whether the latest reconciliation query completed successfully")
        .register(meterRegistry);
  }

  /**
   * Runs the read-only reconciliation query and emits operational identifiers for investigation.
   */
  @Scheduled(
      fixedDelayString = "${application.assessment.reconciliation.interval:5m}",
      initialDelayString = "${application.assessment.reconciliation.interval:5m}")
  public void run() {
    Instant now = clock.instant();
    try {
      StalledAssessments result =
          queryGateway
              .query(
                  new FindStalledAssessmentsQuery(now.minus(threshold)), StalledAssessments.class)
              .join();
      stalledCount.set(result.applications().size());
      oldestAgeSeconds.set(
          result.applications().stream()
              .mapToLong(application -> ageSeconds(application, now))
              .max()
              .orElse(0));
      fresh.set(1);
      meterRegistry
          .counter("application.assessment.reconciliation", "outcome", "success")
          .increment();
      result.applications().forEach(application -> report(application, now));
    } catch (RuntimeException exception) {
      fresh.set(0);
      meterRegistry
          .counter("application.assessment.reconciliation", "outcome", "failure")
          .increment();
      LOG.error("Automatic-assessment reconciliation failed", exception);
      throw exception;
    }
  }

  private void report(StalledAssessment application, Instant now) {
    LOG.warn(
        "Application remains unassessed: applicationId={}, applyApplicationId={},"
            + " applicationVersion={}, submittedAt={}, ageSeconds={}",
        application.applicationId(),
        application.applyApplicationId(),
        application.applicationVersion(),
        application.submittedAt(),
        ageSeconds(application, now));
  }

  private long ageSeconds(StalledAssessment application, Instant now) {
    return Duration.between(application.submittedAt(), now).toSeconds();
  }
}
