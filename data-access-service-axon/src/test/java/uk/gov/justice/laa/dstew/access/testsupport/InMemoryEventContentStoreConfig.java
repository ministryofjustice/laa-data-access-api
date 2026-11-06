package uk.gov.justice.laa.dstew.access.testsupport;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;

/**
 * Replaces the Postgres content store for H2-backed tests. It does not roll back with the event, so
 * transactional behaviour is covered by the Postgres integration tests instead.
 */
@TestConfiguration
public class InMemoryEventContentStoreConfig {

  @Bean
  @Primary
  EventContentStore inMemoryEventContentStore() {
    return new InMemoryEventContentStore();
  }

  static final class InMemoryEventContentStore implements EventContentStore {

    private final Map<String, ContentRow> rows = new ConcurrentHashMap<>();

    @Override
    public void save(List<ContentRow> newRows) {
      newRows.forEach(row -> rows.put(row.eventId(), row));
    }

    @Override
    public Map<String, ObjectNode> findByTags(Collection<String> tags) {
      return rows.values().stream()
          .filter(row -> row.tags().stream().anyMatch(tags::contains))
          .collect(Collectors.toMap(ContentRow::eventId, row -> row.content().deepCopy()));
    }

    @Override
    public Optional<ObjectNode> findByEventId(String eventId) {
      return Optional.ofNullable(rows.get(eventId)).map(row -> row.content().deepCopy());
    }

    @Override
    public Map<String, ObjectNode> findByEventIds(Collection<String> eventIds) {
      return eventIds.stream()
          .filter(rows::containsKey)
          .collect(Collectors.toMap(id -> id, id -> rows.get(id).content().deepCopy()));
    }
  }
}
