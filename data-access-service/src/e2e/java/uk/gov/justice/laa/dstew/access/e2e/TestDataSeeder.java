package uk.gov.justice.laa.dstew.access.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;

public class TestDataSeeder implements AutoCloseable {

  private final Connection connection;
  private final ObjectMapper mapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final ApplicationEntityGenerator applicationGenerator = new ApplicationEntityGenerator();
  private final IndividualEntityGenerator individualGenerator = new IndividualEntityGenerator();

  private final List<UUID> createdApplicationIds = new ArrayList<>();
  private final List<UUID> createdIndividualIds = new ArrayList<>();
  private final List<UUID> createdCaseworkerIds = new ArrayList<>();

  public TestDataSeeder(String url, String username, String password) throws SQLException {
    this.connection = DriverManager.getConnection(url, username, password);
  }

  public UUID seedApplicationWithIndividual() throws Exception {
    boolean wasAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      UUID individualId = insertIndividual(individualGenerator.createDefault());
      UUID applicationId = insertApplication(applicationGenerator.createDefault());
      linkIndividualToApplication(applicationId, individualId);
      connection.commit();
      return applicationId;
    } catch (Exception e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(wasAutoCommit);
    }
  }

  public UUID insertIndividual(IndividualEntity individual) throws Exception {
    UUID id = UUID.randomUUID();
    var sql = """
        INSERT INTO individuals (id, first_name, last_name, date_of_birth,
            individual_content, individual_type, created_at, modified_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?,
            NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')
        """;
    try (var ps = connection.prepareStatement(sql)) {
      ps.setObject(1, id);
      ps.setString(2, individual.getFirstName());
      ps.setString(3, individual.getLastName());
      ps.setObject(4, java.sql.Date.valueOf(individual.getDateOfBirth()));
      ps.setString(5, mapper.writeValueAsString(individual.getIndividualContent()));
      ps.setString(6, individual.getType().name());
      ps.executeUpdate();
    }
    createdIndividualIds.add(id);
    return id;
  }

  public UUID insertApplication(ApplicationEntity app) throws Exception {
    UUID id = app.getId() != null ? app.getId() : UUID.randomUUID();
    var sql = """
        INSERT INTO applications (id, status, laa_reference, office_code,
            application_content, schema_version, apply_application_id,
            submitted_at, used_delegated_functions, category_of_law,
            matter_types, is_auto_granted, created_at, modified_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?,
            NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')
        """;
    try (var ps = connection.prepareStatement(sql)) {
      ps.setObject(1, id);
      ps.setString(2, app.getStatus().name());
      ps.setString(3, app.getLaaReference());
      ps.setString(4, app.getOfficeCode());
      ps.setString(5, mapper.writeValueAsString(app.getApplicationContent()));
      ps.setObject(6, app.getSchemaVersion());
      ps.setObject(7, app.getApplyApplicationId());
      ps.setTimestamp(8, Timestamp.from(app.getSubmittedAt()));
      ps.setObject(9, app.getUsedDelegatedFunctions());
      ps.setString(10, app.getCategoryOfLaw() != null ? app.getCategoryOfLaw().name() : null);
      ps.setString(11, app.getMatterType() != null ? app.getMatterType().name() : null);
      ps.setObject(12, app.getIsAutoGranted());
      ps.executeUpdate();
    }
    createdApplicationIds.add(id);
    return id;
  }

  public void linkIndividualToApplication(UUID applicationId, UUID individualId)
      throws SQLException {
    var sql = "INSERT INTO linked_individuals (application_id, individual_id) VALUES (?, ?)";
    try (var ps = connection.prepareStatement(sql)) {
      ps.setObject(1, applicationId);
      ps.setObject(2, individualId);
      ps.executeUpdate();
    }
  }

  public UUID insertCaseworker(String username) throws SQLException {
    UUID id = UUID.randomUUID();
    var sql = """
        INSERT INTO caseworkers (id, username, created_at, modified_at)
        VALUES (?, ?, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')
        """;
    try (var ps = connection.prepareStatement(sql)) {
      ps.setObject(1, id);
      ps.setString(2, username);
      ps.executeUpdate();
    }
    createdCaseworkerIds.add(id);
    return id;
  }

  public void insertDomainEvent(UUID applicationId) throws SQLException {
    var sql = """
        INSERT INTO domain_events (id, application_id, type, data, created_at, created_by)
        VALUES (?, ?, ?, ?::jsonb, NOW() AT TIME ZONE 'UTC', ?)
        """;
    try (var ps = connection.prepareStatement(sql)) {
      ps.setObject(1, UUID.randomUUID());
      ps.setObject(2, applicationId);
      ps.setString(3, DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER.name());
      ps.setString(4, "{\"eventDescription\": \"Application assigned to caseworker\"}");
      ps.setString(5, "e2e-test");
      ps.executeUpdate();
    }
  }

  public void cleanup() throws SQLException {
    if (!createdApplicationIds.isEmpty()) {
      var sql = "DELETE FROM applications WHERE id = ANY(?)";
      try (var ps = connection.prepareStatement(sql)) {
        ps.setArray(1, connection.createArrayOf("uuid", createdApplicationIds.toArray()));
        ps.executeUpdate();
      }
      createdApplicationIds.clear();
    }
    if (!createdIndividualIds.isEmpty()) {
      var sql = "DELETE FROM individuals WHERE id = ANY(?)";
      try (var ps = connection.prepareStatement(sql)) {
        ps.setArray(1, connection.createArrayOf("uuid", createdIndividualIds.toArray()));
        ps.executeUpdate();
      }
      createdIndividualIds.clear();
    }
    if (!createdCaseworkerIds.isEmpty()) {
      var sql = "DELETE FROM caseworkers WHERE id = ANY(?)";
      try (var ps = connection.prepareStatement(sql)) {
        ps.setArray(1, connection.createArrayOf("uuid", createdCaseworkerIds.toArray()));
        ps.executeUpdate();
      }
      createdCaseworkerIds.clear();
    }
  }

  @Override
  public void close() throws SQLException {
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }
}
