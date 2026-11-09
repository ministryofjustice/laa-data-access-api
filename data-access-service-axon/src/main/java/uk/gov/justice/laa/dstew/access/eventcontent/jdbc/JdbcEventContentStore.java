package uk.gov.justice.laa.dstew.access.eventcontent.jdbc;

import java.sql.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;

/**
 * Stores event content in the {@code event_content} table. The table is schema-qualified because
 * the connection's default schema is not guaranteed to be the one Flyway migrated.
 */
public final class JdbcEventContentStore implements EventContentStore {

  private static final Pattern SCHEMA_NAME = Pattern.compile("[a-z_][a-z0-9_]*");

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final String table;

  /** Creates a store writing to {@code <schema>.event_content}; the schema name is validated. */
  public JdbcEventContentStore(JdbcTemplate jdbc, EventJson json, String schema) {
    if (!SCHEMA_NAME.matcher(schema).matches()) {
      throw new IllegalArgumentException("Invalid schema name: " + schema);
    }
    this.jdbc = jdbc;
    this.mapper = json.mapper();
    this.table = schema + ".event_content";
  }

  @Override
  public void save(List<ContentRow> rows) {
    for (ContentRow row : rows) {
      jdbc.update(
          connection -> {
            var statement =
                connection.prepareStatement(
                    "INSERT INTO " + table + " (event_id, tags, content) VALUES (?, ?, ?::jsonb)");
            Array tags = connection.createArrayOf("text", row.tags().toArray());
            statement.setString(1, row.eventId());
            statement.setArray(2, tags);
            statement.setString(3, mapper.writeValueAsString(row.content()));
            return statement;
          });
    }
  }

  @Override
  public Map<String, ObjectNode> findByTags(Collection<String> tags) {
    Map<String, ObjectNode> content = new HashMap<>();
    if (tags.isEmpty()) {
      return content;
    }
    jdbc.query(
        connection -> {
          var statement =
              connection.prepareStatement(
                  "SELECT event_id, content::text AS content FROM " + table + " WHERE tags && ?");
          statement.setArray(1, connection.createArrayOf("text", tags.toArray()));
          return statement;
        },
        row -> {
          content.put(row.getString("event_id"), read(row.getString("content")));
        });
    return content;
  }

  @Override
  public Optional<ObjectNode> findByEventId(String eventId) {
    return jdbc
        .queryForList(
            "SELECT content::text FROM " + table + " WHERE event_id = ?", String.class, eventId)
        .stream()
        .findFirst()
        .map(this::read);
  }

  @Override
  public Map<String, ObjectNode> findByEventIds(Collection<String> eventIds) {
    Map<String, ObjectNode> content = new HashMap<>();
    if (eventIds.isEmpty()) {
      return content;
    }
    jdbc.query(
        connection -> {
          var statement =
              connection.prepareStatement(
                  "SELECT event_id, content::text AS content FROM "
                      + table
                      + " WHERE event_id = ANY (?)");
          statement.setArray(1, connection.createArrayOf("varchar", eventIds.toArray()));
          return statement;
        },
        row -> {
          content.put(row.getString("event_id"), read(row.getString("content")));
        });
    return content;
  }

  private ObjectNode read(String json) {
    return (ObjectNode) mapper.readTree(json);
  }
}
