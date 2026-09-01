package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListItemType;
import uk.gov.justice.laa.dstew.access.model.WorkListResponse;
import uk.gov.justice.laa.dstew.access.model.WorkListUnassignRequest;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/** Full HTTP/Postgres/Axon integration tests for delivered work-list behaviour. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkListIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void
      givenManualApplication_whenAssignedThenUnassigned_thenItsWorkListViewsAndConflictsAreConsistent() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    createManualApplication(applicationId);
    jdbcTemplate.update(
        "INSERT INTO axon.caseworkers (id, username) VALUES (?, ?)",
        caseworkerId,
        "caseworker@example.com");

    awaitWorkListContains("", applicationId, null, 0L);

    ResponseEntity<Void> assigned =
        restTemplate.exchange(
            assignmentUrl(applicationId, "assign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(caseworkerId, 0L), headers()),
            Void.class);
    assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListContains("?assignedTo=" + caseworkerId, applicationId, caseworkerId, 1L);

    ResponseEntity<Void> duplicate =
        restTemplate.exchange(
            assignmentUrl(applicationId, "assign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(caseworkerId, 0L), headers()),
            Void.class);
    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    ResponseEntity<Void> unassigned =
        restTemplate.exchange(
            assignmentUrl(applicationId, "unassign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListUnassignRequest(1L), headers()),
            Void.class);
    assertThat(unassigned.getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListContains("", applicationId, null, 2L);
  }

  private void createManualApplication(UUID applicationId) {
    ResponseEntity<Void> created =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<Void> ready =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications/"
                + applicationId
                + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL), headers()),
            Void.class);
    assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private void awaitWorkListContains(
      String query, UUID applicationId, UUID expectedAssignee, long expectedAssignmentVersion) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              ResponseEntity<WorkListResponse> response =
                  restTemplate.exchange(
                      "http://localhost:" + port + "/api/v0/work-list" + query,
                      HttpMethod.GET,
                      new HttpEntity<>(headers()),
                      WorkListResponse.class);
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(response.getBody()).isNotNull();
              assertThat(response.getBody().getItems())
                  .filteredOn(item -> item.getItemId().equals(applicationId))
                  .singleElement()
                  .satisfies(
                      item -> {
                        assertThat(item.getItemType()).isEqualTo(WorkListItemType.APPLICATION);
                        assertThat(item.getAssignedTo()).isEqualTo(expectedAssignee);
                        assertThat(item.getAssignmentVersion())
                            .isEqualTo(expectedAssignmentVersion);
                      });
            });
  }

  private String assignmentUrl(UUID itemId, String operation) {
    return "http://localhost:" + port + "/api/v0/work-list/" + itemId + "/" + operation;
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}
