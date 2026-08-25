package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/** Full HTTP/Postgres/Axon integration tests for the GET /applications (list) endpoint. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GetApplicationsIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private QueryGateway queryGateway;

  @Test
  void givenCreatedApplication_whenGetApplications_thenReturnsApplicationInList() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId);
    awaitApplicationProjection(applicationId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getPaging()).isNotNull();
    assertThat(response.getBody().getApplications())
        .extracting(application -> application.getApplicationId())
        .contains(applicationId);
  }

  @Test
  void
      givenCreatedApplication_whenGetApplicationsFilteredByStatus_thenReturnsMatchingApplication() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId);
    awaitApplicationProjection(applicationId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications?status=APPLICATION_SUBMITTED",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getApplications())
        .extracting(application -> application.getApplicationId())
        .contains(applicationId);
  }

  private void createApplication(UUID applicationId) {
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private ApplicationReadModel awaitApplicationProjection(UUID applicationId) {
    return await()
        .alias("application projection to be populated for " + applicationId)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () ->
                queryGateway
                    .query(new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class)
                    .join(),
            java.util.Objects::nonNull);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}
