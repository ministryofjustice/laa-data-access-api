package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.observability.ErrorReporter;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/**
 * Proves the Axon pooled-streaming consolidation fix: a projection failure on a background
 * processor thread (no HTTP caller to propagate to) is still reported through {@link
 * ErrorReporter}, and that the cooldown in {@code ReportingErrorHandler} suppresses the retry-storm
 * of re-reports that Axon's automatic segment retries would otherwise generate.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import({TestJwtDecoderConfig.class, ProjectionErrorReportingIntegrationTest.FaultConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectionErrorReportingIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ApplicationReadRepository applications;
  @Autowired private ProjectionFault fault;
  @MockitoBean private ErrorReporter errorReporter;

  @Test
  void givenPooledStreamingProjectionFailure_whenProcessed_thenReportsOnceThenRecovers()
      throws InterruptedException {
    UUID applicationId = UUID.randomUUID();
    fault.armFor(applicationId);

    postApplication(applicationId);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(errorReporter).report(anyString(), any()));
    // Axon retries the failing segment repeatedly; give it time to retry a few more times before
    // disarming, to prove the cooldown suppresses the resulting retry-storm of re-reports.
    Thread.sleep(2000);
    verify(errorReporter, atLeast(1)).report(anyString(), any());
    verify(errorReporter, atMost(1)).report(anyString(), any());

    fault.disarm();

    await()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(applications.findById(applicationId)).isPresent());
  }

  private void postApplication(UUID applicationId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    restTemplate.postForEntity(
        "/api/v0/applications",
        new HttpEntity<>(validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers),
        Void.class);
  }

  @TestConfiguration
  static class FaultConfig {
    @Bean
    ProjectionFault projectionFault() {
      return new ProjectionFault();
    }
  }

  /**
   * Forces the {@code application-projection} pooled-streaming processor to fail for a single,
   * armed application id — simulating an unexpected projection bug so the error-reporting path can
   * be exercised end-to-end, without permanently breaking the processor for other tests.
   */
  @Aspect
  static class ProjectionFault {
    private volatile UUID armedApplicationId;
    private final AtomicBoolean armed = new AtomicBoolean(false);

    void armFor(UUID applicationId) {
      this.armedApplicationId = applicationId;
      this.armed.set(true);
    }

    void disarm() {
      this.armed.set(false);
    }

    @Around(
        "execution(* uk.gov.justice.laa.dstew.access.query.application.ApplicationProjection.on(uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent,..))")
    Object intercept(ProceedingJoinPoint invocation) throws Throwable {
      ApplicationCreatedEvent event = (ApplicationCreatedEvent) invocation.getArgs()[0];
      if (armed.get() && event.applicationId().equals(armedApplicationId)) {
        throw new IllegalStateException("Simulated projection failure for integration test");
      }
      return invocation.proceed();
    }
  }
}
