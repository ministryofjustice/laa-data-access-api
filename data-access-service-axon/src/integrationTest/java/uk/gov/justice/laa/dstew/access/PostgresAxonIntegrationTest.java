package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresAxonIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private EventStorageEngine eventStorageEngine;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ApplicationReadRepository applicationReadRepository;

  @Test
  void givenPostgresAxonStore_whenHealthRequested_thenReportsUp() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

    List<String> eventStoreTables =
        jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'axon'
              AND table_name IN ('domain_event_entry', 'snapshot_event_entry')
            ORDER BY table_name
            """,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
    assertThat(eventStorageEngine).isInstanceOf(JpaEventStorageEngine.class);
    assertThat(eventStoreTables).containsExactly("domain_event_entry", "snapshot_event_entry");
  }

  @Test
  void givenValidRequest_whenPostApplication_thenPersistsEventAndCurrentStateProjection()
      throws Exception {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");

    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applyApplicationId, applyProceedingId), headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID applicationId =
        UUID.fromString(
            response.getHeaders().getLocation().getPath().replace("/api/v0/applications/", ""));

    ApplicationReadModel projected = awaitProjection(applicationId);
    assertThat(projected.getApplicationId()).isEqualTo(applicationId);
    assertThat(projected.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(projected.getLaaReference()).isEqualTo("LAA-123");
    assertThat(projected.getSchemaVersion()).isEqualTo(2);
    assertThat(projected.getApplicationType()).isEqualTo("APPLY");
    assertThat(projected.getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(projected.getSubmittedAt()).isEqualTo(Instant.parse("2026-07-14T12:30:00Z"));
    assertThat(projected.getOfficeCode()).isEqualTo("1A001B");
    assertThat(projected.getUsedDelegatedFunctions()).isFalse();
    assertThat(projected.getCategoryOfLaw()).isEqualTo("FAMILY");
    assertThat(projected.getMatterType()).isEqualTo("SPECIAL_CHILDREN_ACT");
    assertThat(projected.getCreatedAt()).isNotNull().isEqualTo(projected.getModifiedAt());
    assertThat(projected.getApplicationContent().getId()).isEqualTo(applyApplicationId);
    assertThat(projected.getApplicationContent().getOffice().getCode()).isEqualTo("1A001B");
    assertThat(projected.getIndividuals())
        .singleElement()
        .satisfies(
            individual -> {
              assertThat(individual.individualId()).isNotNull();
              assertThat(individual.firstName()).isEqualTo("Ada");
              assertThat(individual.lastName()).isEqualTo("Lovelace");
              assertThat(individual.dateOfBirth()).isEqualTo(LocalDate.parse("1815-12-10"));
              assertThat(individual.individualContent())
                  .containsExactlyEntriesOf(Map.of("preferredName", "Ada"));
              assertThat(individual.type()).isEqualTo("CLIENT");
            });
    assertThat(projected.getProceedings())
        .singleElement()
        .satisfies(
            proceeding -> {
              assertThat(proceeding.proceedingId()).isNotNull();
              assertThat(proceeding.applyProceedingId()).isEqualTo(applyProceedingId);
              assertThat(proceeding.description()).isEqualTo("Care order");
              assertThat(proceeding.lead()).isTrue();
              assertThat(proceeding.proceedingContent().getId()).isEqualTo(applyProceedingId);
            });

    List<Map<String, Object>> events =
        jdbcTemplate.queryForList(
            "SELECT payload_type, sequence_number FROM axon.domain_event_entry "
                + "WHERE aggregate_identifier = ?",
            applicationId.toString());
    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.get("payload_type"))
                  .isEqualTo(
                      "uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent");
              assertThat(event.get("sequence_number")).isEqualTo(0L);
            });
  }

  private ApplicationReadModel awaitProjection(UUID applicationId) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      var projected = applicationReadRepository.findById(applicationId);
      if (projected.isPresent()) {
        return projected.get();
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Application projection was not populated for " + applicationId);
  }
}
