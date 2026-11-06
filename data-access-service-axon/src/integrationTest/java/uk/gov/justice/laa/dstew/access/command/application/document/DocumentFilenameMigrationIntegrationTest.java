package uk.gov.justice.laa.dstew.access.command.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import uk.gov.justice.laa.dstew.access.BaseIntegrationTest;

class DocumentFilenameMigrationIntegrationTest extends BaseIntegrationTest {

  private static final String SCHEMA = "axon";
  private static final String BEFORE_DOCUMENT_FILENAME = "30";
  private static final String DOCUMENT_FILENAME_VERSION = "31";
  private static final Instant FIRST_VERSION_AT = Instant.parse("2026-09-01T09:00:00Z");
  private static final Instant LATEST_VERSION_AT = Instant.parse("2026-09-02T09:00:00Z");
  private static final Instant DRAFT_UPDATED_AT = Instant.parse("2026-09-03T09:00:00Z");

  @Test
  void givenLegacyDrafts_whenMigrated_thenBackfillsNonBlankDraftFilenames() {
    DataSource dataSource = newMigratedDatabase();
    JdbcTemplate template = new JdbcTemplate(dataSource);
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationDocumentId = UUID.randomUUID();
    UUID blankDocumentId = UUID.randomUUID();
    UUID priorAuthorityDocumentId = UUID.randomUUID();
    insertApplicationDraft(
        template,
        applicationId,
        Map.of(applicationDocumentId, "application.PDF", blankDocumentId, "   "));
    insertPriorAuthorityDraft(
        template, priorAuthorityId, applicationId, Map.of(priorAuthorityDocumentId, "prior.PDF"));

    migrateTo(dataSource, DOCUMENT_FILENAME_VERSION);

    assertThat(storedFilenames(template))
        .containsExactlyInAnyOrder(
            new StoredFilename(
                applicationId, applicationDocumentId, "application.PDF", DRAFT_UPDATED_AT),
            new StoredFilename(
                priorAuthorityId, priorAuthorityDocumentId, "prior.PDF", DRAFT_UPDATED_AT));
  }

  @Test
  void givenMultipleDataVersions_whenMigrated_thenBackfillsOnlyLatestVersions() {
    DataSource dataSource = newMigratedDatabase();
    JdbcTemplate template = new JdbcTemplate(dataSource);
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID olderApplicationDocumentId = UUID.randomUUID();
    UUID latestApplicationDocumentId = UUID.randomUUID();
    UUID olderPriorAuthorityDocumentId = UUID.randomUUID();
    UUID latestPriorAuthorityDocumentId = UUID.randomUUID();
    insertApplicationData(
        template,
        applicationId,
        0L,
        Map.of(olderApplicationDocumentId, "older.PDF"),
        FIRST_VERSION_AT);
    insertApplicationData(
        template,
        applicationId,
        1L,
        Map.of(latestApplicationDocumentId, "latest.PDF"),
        LATEST_VERSION_AT);
    insertPriorAuthorityData(
        template,
        priorAuthorityId,
        applicationId,
        0L,
        Map.of(olderPriorAuthorityDocumentId, "older-prior.PDF"),
        FIRST_VERSION_AT);
    insertPriorAuthorityData(
        template,
        priorAuthorityId,
        applicationId,
        1L,
        Map.of(latestPriorAuthorityDocumentId, "latest-prior.PDF"),
        LATEST_VERSION_AT);

    migrateTo(dataSource, DOCUMENT_FILENAME_VERSION);

    assertThat(storedFilenames(template))
        .containsExactlyInAnyOrder(
            new StoredFilename(
                applicationId, latestApplicationDocumentId, "latest.PDF", LATEST_VERSION_AT),
            new StoredFilename(
                priorAuthorityId,
                latestPriorAuthorityDocumentId,
                "latest-prior.PDF",
                LATEST_VERSION_AT));
  }

  @Test
  void givenSameDocumentUnderTwoOwners_whenMigrated_thenMigrationFailsAndTableIsNotCreated() {
    DataSource dataSource = newMigratedDatabase();
    JdbcTemplate template = new JdbcTemplate(dataSource);
    UUID documentId = UUID.randomUUID();
    insertApplicationDraft(template, UUID.randomUUID(), Map.of(documentId, "application.PDF"));
    insertPriorAuthorityDraft(
        template, UUID.randomUUID(), UUID.randomUUID(), Map.of(documentId, "prior.PDF"));

    assertThatThrownBy(() -> migrateTo(dataSource, DOCUMENT_FILENAME_VERSION))
        .hasMessageContaining("more than one owner");
    assertThat(
            template.queryForObject(
                "SELECT to_regclass('" + SCHEMA + ".document_filename') IS NULL", Boolean.class))
        .isTrue();
  }

  private DataSource newMigratedDatabase() {
    String databaseName = "document_filename_" + UUID.randomUUID().toString().replace("-", "");
    jdbcTemplate.execute("CREATE DATABASE " + databaseName);
    DataSource dataSource =
        new DriverManagerDataSource(
            "jdbc:postgresql://%s:%d/%s?currentSchema=%s"
                .formatted(postgres.getHost(), postgres.getMappedPort(5432), databaseName, SCHEMA),
            postgres.getUsername(),
            postgres.getPassword());
    migrateTo(dataSource, BEFORE_DOCUMENT_FILENAME);
    return dataSource;
  }

  private void migrateTo(DataSource dataSource, String target) {
    Flyway.configure()
        .dataSource(dataSource)
        .schemas(SCHEMA)
        .defaultSchema(SCHEMA)
        .createSchemas(true)
        .target(MigrationVersion.fromVersion(target))
        .load()
        .migrate();
  }

  private void insertApplicationDraft(
      JdbcTemplate template, UUID applicationId, Map<UUID, String> filenames) {
    template.update(
        "INSERT INTO application_draft (application_id, payload, payload_hash, created_at, updated_at)"
            + " VALUES (?, ?::jsonb, 'hash', ?, ?)",
        applicationId,
        payload(filenames),
        timestamp(DRAFT_UPDATED_AT),
        timestamp(DRAFT_UPDATED_AT));
  }

  private void insertPriorAuthorityDraft(
      JdbcTemplate template,
      UUID priorAuthorityId,
      UUID applicationId,
      Map<UUID, String> filenames) {
    template.update(
        "INSERT INTO prior_authority_draft"
            + " (prior_authority_id, application_id, payload, payload_hash, created_at, updated_at)"
            + " VALUES (?, ?, ?::jsonb, 'hash', ?, ?)",
        priorAuthorityId,
        applicationId,
        payload(filenames),
        timestamp(DRAFT_UPDATED_AT),
        timestamp(DRAFT_UPDATED_AT));
  }

  private void insertApplicationData(
      JdbcTemplate template,
      UUID applicationId,
      long version,
      Map<UUID, String> filenames,
      Instant createdAt) {
    template.update(
        "INSERT INTO application_data (application_id, version, payload, payload_hash, created_at)"
            + " VALUES (?, ?, ?::jsonb, 'hash', ?)",
        applicationId,
        version,
        payload(filenames),
        timestamp(createdAt));
  }

  private void insertPriorAuthorityData(
      JdbcTemplate template,
      UUID priorAuthorityId,
      UUID applicationId,
      long dataVersion,
      Map<UUID, String> filenames,
      Instant createdAt) {
    template.update(
        "INSERT INTO prior_authority_data"
            + " (prior_authority_id, data_version, application_id, payload, payload_hash,"
            + " created_at) VALUES (?, ?, ?, ?::jsonb, 'hash', ?)",
        priorAuthorityId,
        dataVersion,
        applicationId,
        payload(filenames),
        timestamp(createdAt));
  }

  private List<StoredFilename> storedFilenames(JdbcTemplate template) {
    return template.query(
        "SELECT owner_id, document_id, filename, created_at FROM document_filename",
        (rs, rowNum) ->
            new StoredFilename(
                rs.getObject("owner_id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("filename"),
                rs.getTimestamp("created_at").toInstant()));
  }

  private static String payload(Map<UUID, String> filenames) {
    String entries =
        filenames.entrySet().stream()
            .map(entry -> "\"%s\": \"%s\"".formatted(entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(", "));
    return "{\"documentFilenames\": {" + entries + "}}";
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.from(instant);
  }

  private record StoredFilename(
      UUID ownerId, UUID documentId, String filename, Instant createdAt) {}
}
