package uk.gov.justice.laa.dstew.dataaccesstools.cli.local;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.ApiException;

/** Reads aggregate streams from the local JDBC-backed Axon event store. */
final class LocalEventStoreReader {
  private final String jdbcUrl;
  private final String username;
  private final String password;
  private final String schema;

  LocalEventStoreReader(String jdbcUrl, String username, String password, String schema) {
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
    this.schema = validateSchema(schema);
  }

  List<StoredEvent> read(UUID aggregateId) {
    String sql =
        "SELECT event_identifier, sequence_number, time_stamp, payload_type, payload, meta_data "
            + "FROM \""
            + schema
            + "\".domain_event_entry WHERE aggregate_identifier = ? ORDER BY sequence_number";
    try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, aggregateId.toString());
      try (ResultSet results = statement.executeQuery()) {
        List<StoredEvent> events = new ArrayList<>();
        while (results.next()) {
          events.add(
              new StoredEvent(
                  aggregateId,
                  results.getString("event_identifier"),
                  results.getLong("sequence_number"),
                  results.getString("time_stamp"),
                  results.getString("payload_type"),
                  content(results.getBytes("payload")),
                  content(results.getBytes("meta_data"))));
        }
        return events;
      }
    } catch (SQLException exception) {
      throw new ApiException(
          "Unable to read local Axon event store: " + exception.getMessage(), exception);
    }
  }

  private static String validateSchema(String schema) {
    if (schema == null || !schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException("--axon-schema must be a PostgreSQL identifier");
    }
    return schema;
  }

  private static String content(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    String text = new String(bytes, StandardCharsets.UTF_8);
    return text.chars()
            .allMatch(
                character ->
                    character >= 32 || character == '\n' || character == '\r' || character == '\t')
        ? text
        : "base64:" + Base64.getEncoder().encodeToString(bytes);
  }

  record StoredEvent(
      UUID aggregateId,
      String eventIdentifier,
      long sequenceNumber,
      String timestamp,
      String payloadType,
      String payload,
      String metadata) {}
}
