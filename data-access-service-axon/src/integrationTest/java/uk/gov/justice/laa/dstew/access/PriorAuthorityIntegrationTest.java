package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.Apportionment;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.BillingType;
import uk.gov.justice.laa.dstew.access.model.CounselDetails;
import uk.gov.justice.laa.dstew.access.model.CounselType;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.ExpertCosts;
import uk.gov.justice.laa.dstew.access.model.ExpertDetails;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.TimeRequested;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadRepository;

/** Full HTTP/Postgres/Axon integration tests for the Prior Authority submission endpoint. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PriorAuthorityIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ApplicationReadRepository applicationReadRepository;

  @Autowired private PriorAuthorityReadRepository priorAuthorityReadRepository;

  @Autowired private QueryGateway queryGateway;

  @Test
  void givenGrantedApplication_whenCreatePriorAuthority_thenPersistsEventDataAndProjection()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    createApplication(applicationId, applyProceedingId);
    awaitApplicationProjection(applicationId);
    grantApplication(applicationId);
    awaitApplicationProjectionVersion(applicationId, 1L);

    CreatePriorAuthorityRequest request = hourlyExpertRequest();
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorityUrl(applicationId), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    CreatePriorAuthorityResponse body =
        objectMapper.readValue(response.getBody(), CreatePriorAuthorityResponse.class);
    assertThat(body.getSubmissionId()).isNotNull();
    assertThat(body.getSubmittedAt()).isNotNull();
    UUID submissionId = body.getSubmissionId();
    assertThat(response.getHeaders().getLocation())
        .isNotNull()
        .satisfies(loc -> assertThat(loc.toString()).endsWith("/" + submissionId));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payload ->> 'applicationId' FROM axon.prior_authority_data"
                    + " WHERE submission_id = ? AND data_version = 0",
                String.class,
                submissionId))
        .isEqualTo(applicationId.toString());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payload -> 'content' ->> 'priorAuthorityType'"
                    + " FROM axon.prior_authority_data"
                    + " WHERE submission_id = ? AND data_version = 0",
                String.class,
                submissionId))
        .isEqualTo("EXPERT");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT length(payload_hash) FROM axon.prior_authority_data"
                    + " WHERE submission_id = ? AND data_version = 0",
                Integer.class,
                submissionId))
        .isEqualTo(64);

    List<Map<String, Object>> events =
        jdbcTemplate.queryForList(
            "SELECT payload_type, sequence_number FROM axon.domain_event_entry"
                + " WHERE aggregate_identifier = ?",
            submissionId.toString());
    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.get("payload_type"))
                  .isEqualTo(
                      "uk.gov.justice.laa.dstew.access.command.application.priorauthority"
                          + ".PriorAuthorityCreatedEvent");
              assertThat(event.get("sequence_number")).isEqualTo(0L);
            });

    PriorAuthorityReadModel projection =
        priorAuthorityReadRepository
            .findById(submissionId)
            .orElseThrow(() -> new AssertionError("Prior authority projection not found"));
    assertThat(projection.getApplicationId()).isEqualTo(applicationId);
    assertThat(projection.getStatus()).isEqualTo("PENDING");
    assertThat(projection.getCreatedAt()).isNotNull();
  }

  @Test
  void givenMissingApplication_whenCreatePriorAuthority_thenReturnsNotFound() {
    UUID nonexistentApplicationId = UUID.randomUUID();
    CreatePriorAuthorityRequest request = disbursementRequest();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorityUrl(nonexistentApplicationId),
            new HttpEntity<>(request, headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_data WHERE application_id = ?",
                Integer.class,
                nonexistentApplicationId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT submission_id FROM axon.prior_authority_current_state"
                    + " WHERE application_id = ?",
                nonexistentApplicationId))
        .isEmpty();
  }

  @Test
  void givenUngrantedApplication_whenCreatePriorAuthority_thenReturnsBadRequest() throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    createApplication(applicationId, applyProceedingId);
    awaitApplicationProjection(applicationId);

    CreatePriorAuthorityRequest request = fixedRateExpertRequest();
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorityUrl(applicationId), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("GRANTED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_data WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT submission_id FROM axon.prior_authority_current_state"
                    + " WHERE application_id = ?",
                applicationId))
        .isEmpty();
  }

  @Test
  void givenGrantedApplication_whenCreateExpertWithoutExpertDetails_thenReturnsBadRequest()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    createApplication(applicationId, applyProceedingId);
    awaitApplicationProjection(applicationId);
    grantApplication(applicationId);
    awaitApplicationProjectionVersion(applicationId, 1L);

    CreatePriorAuthorityRequest request =
        new CreatePriorAuthorityRequest(PriorAuthorityType.EXPERT, "Need expert assessment");

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorityUrl(applicationId), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_data WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT submission_id FROM axon.prior_authority_current_state"
                    + " WHERE application_id = ?",
                applicationId))
        .isEmpty();
  }

  @ParameterizedTest
  @EnumSource(CounselType.class)
  void givenGrantedApplication_whenCreateCounselPriorAuthority_thenReturnsCreated(
      CounselType counselType) {
    UUID applicationId = grantedApplication();
    CreatePriorAuthorityRequest request =
        CreatePriorAuthorityRequest.builder()
            .priorAuthorityType(PriorAuthorityType.COUNSEL)
            .justification("Specialist counsel is required")
            .counselDetails(CounselDetails.builder().counselType(counselType).build())
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorityUrl(applicationId), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void givenGrantedApplication_whenCreateDisbursementPriorAuthority_thenReturnsCreated() {
    UUID applicationId = grantedApplication();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorityUrl(applicationId),
            new HttpEntity<>(disbursementRequest(), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void givenGrantedApplication_whenJustificationIsAtMaximumLength_thenReturnsCreated() {
    UUID applicationId = grantedApplication();
    CreatePriorAuthorityRequest request = fixedRateExpertRequest();
    request.setJustification("x".repeat(10_000));

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorityUrl(applicationId), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPriorAuthorityRequests")
  void givenGrantedApplication_whenPriorAuthorityPayloadViolatesSchema_thenReturnsBadRequest(
      String description, String payload) {
    UUID applicationId = grantedApplication();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorityUrl(applicationId), new HttpEntity<>(payload, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertNoPriorAuthorityPersisted(applicationId);
  }

  private void createApplication(UUID applicationId, UUID applyProceedingId) {
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, applyProceedingId), headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private UUID grantedApplication() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId, UUID.randomUUID());
    awaitApplicationProjection(applicationId);
    grantApplication(applicationId);
    awaitApplicationProjectionVersion(applicationId, 1L);
    return applicationId;
  }

  private void assertNoPriorAuthorityPersisted(UUID applicationId) {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_data WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT submission_id FROM axon.prior_authority_current_state"
                    + " WHERE application_id = ?",
                applicationId))
        .isEmpty();
  }

  private void grantApplication(UUID applicationId) {
    ResponseEntity<Void> response =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications/"
                + applicationId
                + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(
                new AutoGrantedOutcomeRequest(
                    AutoGrantOutcome.AUTOGRANTED, Map.of("certificateNumber", "PA-CERT-001")),
                headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
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

  private ApplicationReadModel awaitApplicationProjectionVersion(UUID applicationId, long version) {
    return await()
        .alias("application projection to reach version " + version + " for " + applicationId)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () ->
                queryGateway
                    .query(new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class)
                    .join(),
            projected -> projected != null && projected.getApplicationDataVersion() == version);
  }

  private String priorAuthorityUrl(UUID applicationId) {
    return "http://localhost:"
        + port
        + "/api/v0/applications/"
        + applicationId
        + "/prior-authority";
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static Stream<Arguments> invalidPriorAuthorityRequests() {
    return Stream.of(
        Arguments.of("missing priorAuthorityType", "{\"justification\":\"Required\"}"),
        Arguments.of(
            "unknown priorAuthorityType",
            "{\"priorAuthorityType\":\"UNKNOWN\",\"justification\":\"Required\"}"),
        Arguments.of("missing justification", "{\"priorAuthorityType\":\"COUNSEL\"}"),
        Arguments.of(
            "justification exceeds maximum length",
            "{\"priorAuthorityType\":\"COUNSEL\",\"justification\":\"%s\",\"counselDetails\":{\"counselType\":\"KINGS_COUNSEL_ALONE\"}}"
                .formatted("x".repeat(10_001))),
        Arguments.of(
            "counsel without counselDetails",
            "{\"priorAuthorityType\":\"COUNSEL\",\"justification\":\"Required\"}"),
        Arguments.of(
            "counsel without counselType",
            "{\"priorAuthorityType\":\"COUNSEL\",\"justification\":\"Required\",\"counselDetails\":{}}"),
        Arguments.of(
            "unknown counselType",
            "{\"priorAuthorityType\":\"COUNSEL\",\"justification\":\"Required\",\"counselDetails\":{\"counselType\":\"JUNIOR\"}}"),
        Arguments.of(
            "disbursement without disbursementDetails",
            "{\"priorAuthorityType\":\"DISBURSEMENT\",\"justification\":\"Required\"}"),
        Arguments.of(
            "blank disbursement purpose",
            "{\"priorAuthorityType\":\"DISBURSEMENT\",\"justification\":\"Required\",\"disbursementDetails\":{\"disbursementPurpose\":\"\",\"disbursementAmount\":1}}"),
        Arguments.of(
            "non-positive disbursement amount",
            "{\"priorAuthorityType\":\"DISBURSEMENT\",\"justification\":\"Required\",\"disbursementDetails\":{\"disbursementPurpose\":\"Interpreter\",\"disbursementAmount\":0}}"),
        Arguments.of(
            "blank expert details fields",
            "{\"priorAuthorityType\":\"EXPERT\",\"justification\":\"Required\",\"expertDetails\":{\"expertType\":\"\",\"expertFullName\":\"\",\"expertPostcode\":\"\",\"expertCosts\":{\"billingType\":\"FIXED_RATE\",\"totalAmount\":1,\"costsSharedWithOtherParties\":false}}}"),
        Arguments.of(
            "expert costs without required fields",
            "{\"priorAuthorityType\":\"EXPERT\",\"justification\":\"Required\",\"expertDetails\":{\"expertType\":\"Expert\",\"expertFullName\":\"Name\",\"expertPostcode\":\"AB1\",\"expertCosts\":{}}}"),
        Arguments.of(
            "hourly expert without hourly rate",
            expertPayload(
                "{\"billingType\":\"HOURLY\",\"timeRequested\":{\"hours\":1,\"minutes\":0},\"totalAmount\":1,\"costsSharedWithOtherParties\":false}")),
        Arguments.of(
            "hourly expert without requested time",
            expertPayload(
                "{\"billingType\":\"HOURLY\",\"hourlyRate\":1,\"totalAmount\":1,\"costsSharedWithOtherParties\":false}")),
        Arguments.of(
            "hourly expert with non-positive rate",
            expertPayload(
                "{\"billingType\":\"HOURLY\",\"hourlyRate\":0,\"timeRequested\":{\"hours\":1,\"minutes\":0},\"totalAmount\":1,\"costsSharedWithOtherParties\":false}")),
        Arguments.of(
            "shared costs without apportionment",
            expertPayload(
                "{\"billingType\":\"FIXED_RATE\",\"totalAmount\":1,\"costsSharedWithOtherParties\":true}")),
        Arguments.of(
            "requested time outside permitted range",
            expertPayload(
                "{\"billingType\":\"HOURLY\",\"hourlyRate\":1,\"timeRequested\":{\"hours\":-1,\"minutes\":60},\"totalAmount\":1,\"costsSharedWithOtherParties\":false}")),
        Arguments.of(
            "apportionment below minimum parties",
            expertPayload(
                "{\"billingType\":\"FIXED_RATE\",\"totalAmount\":1,\"costsSharedWithOtherParties\":true,\"apportionment\":{\"partiesSharingCosts\":1,\"clientShareAmount\":1}}")));
  }

  private static String expertPayload(String expertCosts) {
    return "{\"priorAuthorityType\":\"EXPERT\",\"justification\":\"Required\",\"expertDetails\":{\"expertType\":\"Expert\",\"expertFullName\":\"Name\",\"expertPostcode\":\"AB1\",\"expertCosts\":%s}}"
        .formatted(expertCosts);
  }

  private CreatePriorAuthorityRequest hourlyExpertRequest() {
    return CreatePriorAuthorityRequest.builder()
        .priorAuthorityType(PriorAuthorityType.EXPERT)
        .justification("Expert witness required for specialist evidence")
        .expertDetails(
            ExpertDetails.builder()
                .expertType("Forensic Accountant")
                .expertFullName("Dr. Jane Expert")
                .expertPostcode("SW1A 2AA")
                .expertCosts(
                    ExpertCosts.builder()
                        .billingType(BillingType.HOURLY)
                        .hourlyRate(300.0)
                        .timeRequested(new TimeRequested(3, 30))
                        .totalAmount(1050.0)
                        .costsSharedWithOtherParties(true)
                        .apportionment(new Apportionment(2, 525.0))
                        .build())
                .build())
        .build();
  }

  private CreatePriorAuthorityRequest fixedRateExpertRequest() {
    return CreatePriorAuthorityRequest.builder()
        .priorAuthorityType(PriorAuthorityType.EXPERT)
        .justification("Expert witness required")
        .expertDetails(
            ExpertDetails.builder()
                .expertType("Pathologist")
                .expertFullName("Dr. Fixed Rate")
                .expertPostcode("EC1A 1BB")
                .expertCosts(
                    ExpertCosts.builder()
                        .billingType(BillingType.FIXED_RATE)
                        .totalAmount(900.0)
                        .costsSharedWithOtherParties(false)
                        .build())
                .build())
        .build();
  }

  private CreatePriorAuthorityRequest disbursementRequest() {
    return CreatePriorAuthorityRequest.builder()
        .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
        .justification("Interpreter costs for proceedings")
        .disbursementDetails(
            DisbursementDetails.builder()
                .disbursementPurpose("Court interpreter")
                .disbursementAmount(150.0)
                .build())
        .build();
  }
}
