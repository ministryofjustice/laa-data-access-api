package uk.gov.justice.laa.dstew.access.massgenerator.perf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.datafaker.Faker;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullJsonGenerator;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

/**
 * API performance test runner.
 *
 * <p>Activated with {@code --spring.profiles.active=perf}.
 *
 * <p>For each iteration it calls:
 *
 * <ol>
 *   <li>POST /api/v0/applications (always)
 *   <li>PATCH /api/v0/applications/{id}/decision (at configurable rate)
 * </ol>
 *
 * Latency is measured per call and a percentile report is printed on completion.
 */
@Profile("perf")
@Component
public class ApiPerformanceTestRunner implements CommandLineRunner {

  private final ApiClient apiClient;
  private final PerformanceTestProperties props;
  private final ObjectMapper objectMapper;

  public ApiPerformanceTestRunner(
      ApiClient apiClient, PerformanceTestProperties props, ObjectMapper objectMapper) {
    this.apiClient = apiClient;
    this.props = props;
    this.objectMapper = objectMapper;
  }

  @Override
  public void run(String... args) throws Exception {
    int iterations = props.getIterations();
    int concurrency = props.getConcurrency();
    double decideRate = props.getDecideRate();
    long thinkTimeMinMs = props.getThinkTimeMinMs();
    long thinkTimeMaxMs = Math.max(props.getThinkTimeMaxMs(), thinkTimeMinMs);

    System.out.printf("%nAPI Performance Test started at %s%n", Instant.now());
    System.out.printf("  Target     : %s%n", props.getBaseUrl());
    System.out.printf("  Iterations : %d%n", iterations);
    System.out.printf("  Concurrency: %d thread(s)%n", concurrency);
    System.out.printf("  Decide rate: %.0f%%%n", decideRate * 100);
    if (thinkTimeMinMs > 0 || thinkTimeMaxMs > 0) {
      System.out.printf("  Think time : %d – %d ms%n%n", thinkTimeMinMs, thinkTimeMaxMs);
    } else {
      System.out.printf("  Think time : none (synthetic stress mode)%n%n");
    }

    PerformanceMetrics createMetrics = new PerformanceMetrics("POST /api/v0/applications");
    PerformanceMetrics decideMetrics =
        new PerformanceMetrics("PATCH /api/v0/applications/{id}/decision");

    AtomicInteger completedCount = new AtomicInteger(0);
    Faker faker = new Faker();

    long wallStart = System.currentTimeMillis();

    ExecutorService executor = Executors.newFixedThreadPool(concurrency);
    List<Future<?>> futures = new ArrayList<>(iterations);

    for (int i = 0; i < iterations; i++) {
      futures.add(
          executor.submit(
              (Callable<Void>)
                  () -> {
                    // 1. Build a rich ApplicationCreateRequest from FullJsonGenerator
                    ApplicationContent content = new FullJsonGenerator().createDefault();

                    IndividualCreateRequest individual =
                        IndividualCreateRequest.builder()
                            .firstName(faker.name().firstName())
                            .lastName(faker.name().lastName())
                            .dateOfBirth(
                                LocalDate.now().minusYears(faker.number().numberBetween(18, 80)))
                            .details(new HashMap<>(Map.of("source", "perf-test")))
                            .type(IndividualType.CLIENT)
                            .build();

                    ApplicationCreateRequest createRequest =
                        ApplicationCreateRequest.builder()
                            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                            .laaReference(content.getLaaReference())
                            .individuals(List.of(individual))
                            .applicationContent(objectMapper.convertValue(content, Map.class))
                            .build();

                    // 2. POST create – measure latency, capture returned application ID
                    UUID applicationId = apiClient.createApplication(createRequest, createMetrics);

                    // 3. Optionally PATCH decision
                    if (applicationId != null
                        && faker.number().randomDouble(2, 0, 1) < decideRate) {

                      // Think time: simulate caseworker review between create and decision.
                      // Without this, all threads hammer the same tables simultaneously which
                      // is not representative of real usage.
                      if (thinkTimeMaxMs > 0) {
                        long delay =
                            thinkTimeMinMs == thinkTimeMaxMs
                                ? thinkTimeMinMs
                                : thinkTimeMinMs
                                    + (long) (Math.random() * (thinkTimeMaxMs - thinkTimeMinMs));
                        if (delay > 0) {
                          Thread.sleep(delay);
                        }
                      }

                      // Fetch the real proceeding IDs persisted by the API —
                      // the locally-generated IDs in `content` are not used by the server
                      List<UUID> proceedingIds =
                          apiClient.getApplicationProceedingIds(applicationId);

                      if (!proceedingIds.isEmpty()) {

                        if (thinkTimeMaxMs > 0) {
                          long delay =
                              thinkTimeMinMs == thinkTimeMaxMs
                                  ? thinkTimeMinMs
                                  : thinkTimeMinMs
                                      + (long) (Math.random() * (thinkTimeMaxMs - thinkTimeMinMs));
                          if (delay > 0) {
                            Thread.sleep(delay);
                          }
                        }

                        MakeDecisionRequest decisionRequest =
                            buildDecisionRequest(proceedingIds, faker);
                        apiClient.makeDecision(applicationId, decisionRequest, decideMetrics);
                      }
                    }

                    int done = completedCount.incrementAndGet();
                    if (done % 100 == 0) {
                      System.out.printf("  Progress: %d / %d%n", done, iterations);
                    }
                    return null;
                  }));
    }

    executor.shutdown();

    // Collect results; log any task-level exceptions without aborting the run
    for (Future<?> f : futures) {
      try {
        f.get();
      } catch (ExecutionException e) {
        System.err.printf(
            "[ApiPerformanceTestRunner] Task failed: %s%n", e.getCause().getMessage());
      }
    }

    executor.awaitTermination(30, TimeUnit.MINUTES);

    long wallMs = System.currentTimeMillis() - wallStart;
    long wallMin = TimeUnit.MILLISECONDS.toMinutes(wallMs);
    long wallSec = TimeUnit.MILLISECONDS.toSeconds(wallMs) % 60;

    printReport(createMetrics, decideMetrics, iterations, wallMin, wallSec);
  }

  // -------------------------------------------------------------------------

  private MakeDecisionRequest buildDecisionRequest(List<UUID> proceedingIds, Faker faker) {
    DecisionStatus overallDecision =
        faker
            .options()
            .option(
                DecisionStatus.REFUSED,
                DecisionStatus.REFUSED,
                DecisionStatus.REFUSED,
                DecisionStatus.REFUSED,
                DecisionStatus.REFUSED,
                DecisionStatus.REFUSED);

    List<MakeDecisionProceedingRequest> proceedingRequests = new ArrayList<>();
    for (UUID proceedingId : proceedingIds) {
      MeritsDecisionStatus meritsStatus =
          overallDecision == DecisionStatus.GRANTED
              ? MeritsDecisionStatus.GRANTED
              : MeritsDecisionStatus.REFUSED;

      MeritsDecisionDetailsRequest meritsDetails =
          MeritsDecisionDetailsRequest.builder()
              .decision(meritsStatus)
              .justification(faker.lorem().sentence())
              .reason(faker.lorem().sentence())
              .build();

      proceedingRequests.add(
          MakeDecisionProceedingRequest.builder()
              .proceedingId(proceedingId)
              .meritsDecision(meritsDetails)
              .build());
    }

    return MakeDecisionRequest.builder()
        .overallDecision(overallDecision)
        .proceedings(proceedingRequests)
        .autoGranted(overallDecision == DecisionStatus.GRANTED)
        .eventHistory(
            EventHistoryRequest.builder().eventDescription("Performance test decision").build())
        .applicationVersion(0L)
        .build();
  }

  private void printReport(
      PerformanceMetrics createMetrics,
      PerformanceMetrics decideMetrics,
      int iterations,
      long wallMin,
      long wallSec) {
    System.out.println();
    System.out.println("========================================");
    System.out.println("     API Performance Test Report");
    System.out.println("========================================");
    System.out.printf("  Iterations           : %d%n", iterations);
    System.out.printf("  Concurrency          : %d%n", props.getConcurrency());
    System.out.printf("  Target               : %s%n", props.getBaseUrl());
    long min = props.getThinkTimeMinMs();
    long max = Math.max(props.getThinkTimeMaxMs(), min);
    if (max > 0) {
      System.out.printf("  Think time           : %d – %d ms%n", min, max);
    } else {
      System.out.printf("  Think time           : none%n");
    }
    System.out.println();
    printMetrics(createMetrics);
    System.out.println();
    printMetrics(decideMetrics);
    System.out.println();
    System.out.printf("  Total wall-clock time: %d min %02d sec%n", wallMin, wallSec);
    System.out.println("========================================");
  }

  private void printMetrics(PerformanceMetrics m) {
    int requests = m.getRequestCount();
    long errors = m.getErrorCount();
    double errorPct = requests + errors == 0 ? 0 : 100.0 * errors / (requests + errors);

    System.out.printf("  -- %s --%n", m.getOperationName());
    System.out.printf("  Requests             : %d%n", requests);
    System.out.printf("  Errors               : %d (%.1f%%)%n", errors, errorPct);
    if (requests > 0) {
      System.out.printf("  Mean latency         : %.0f ms%n", m.meanMs());
      System.out.printf("  p50                  : %d ms%n", m.p50Ms());
      System.out.printf("  p95                  : %d ms%n", m.p95Ms());
      System.out.printf("  p99                  : %d ms%n", m.p99Ms());
      System.out.printf("  Min / Max            : %d ms / %d ms%n", m.minMs(), m.maxMs());
    } else {
      System.out.println("  (no successful requests)");
    }
  }
}
