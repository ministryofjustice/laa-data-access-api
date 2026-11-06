package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.observability.ErrorReporter;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.access.usecase.application.ApplicationQueryUseCase;

/**
 * Full HTTP/Postgres integration tests proving that {@link ErrorReporter} is actually invoked (or
 * not invoked) at the real HTTP boundary — not just that the correct status code is returned.
 *
 * <p>Existing integration tests ({@code ApplicationLinkingIntegrationTest}, {@code
 * PriorAuthorityDraftIntegrationTest}) already assert the resulting HTTP status codes for these
 * scenarios; this class closes the separate gap that nothing previously asserted on {@code
 * ErrorReporter} itself through a live Spring context and HTTP call.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ErrorReportingHttpBoundaryIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @MockitoSpyBean private ErrorReporter errorReporter;

  @MockitoSpyBean private ApplicationQueryUseCase applicationQueryUseCase;

  @Test
  void givenMalformedJsonLinkRequest_whenApplicationsAreLinked_thenNoErrorIsReported() {
    UUID sourceApplicationId = UUID.randomUUID();
    createApplication(sourceApplicationId);
    String malformedRequestBody =
        """
        {
          "applicationId": "%s",
          "linkType": "OTHER"
        }
        """
            .formatted(UUID.randomUUID());

    ResponseEntity<String> response =
        linkApplicationWithRawJson(sourceApplicationId, malformedRequestBody);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(errorReporter, never()).report(anyString(), any());
  }

  @Test
  void givenUnknownApplicationId_whenGetApplicationById_thenNoErrorIsReported() {
    UUID unknownApplicationId = UUID.randomUUID();

    ResponseEntity<String> response = getApplication(unknownApplicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(errorReporter, never()).report(anyString(), any());
  }

  @Test
  void givenUnexpectedFailure_whenGetApplicationById_thenErrorIsReportedAndFiveHundredReturned() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId);
    RuntimeException simulatedFailure = new RuntimeException("Simulated unexpected failure");
    doThrow(simulatedFailure).when(applicationQueryUseCase).getApplicationDetail(eq(applicationId));

    ResponseEntity<String> response = getApplication(applicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    verify(errorReporter).report(anyString(), same(simulatedFailure));
  }

  private void createApplication(UUID applicationId) {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers()), Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private ResponseEntity<String> linkApplicationWithRawJson(UUID sourceId, String requestBody) {
    HttpHeaders rawJsonHeaders = headers();
    rawJsonHeaders.setContentType(MediaType.APPLICATION_JSON);
    return restTemplate.exchange(
        "/api/v0/applications/" + sourceId + "/link",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, rawJsonHeaders),
        String.class);
  }

  private ResponseEntity<String> getApplication(UUID applicationId) {
    return restTemplate.exchange(
        "/api/v0/applications/" + applicationId,
        HttpMethod.GET,
        new HttpEntity<>(headers()),
        String.class);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}
