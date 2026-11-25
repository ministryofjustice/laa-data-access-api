package uk.gov.justice.laa.dstew.access.eventcontent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.node.ObjectNode;

/**
 * Port for storing event content apart from the event. Content must be written in the same
 * transaction as its event, so they commit or roll back together. Rows are never deleted: erasure
 * replaces their content.
 */
public interface EventContentStore {

  /** One event's content and the stream tags used to find it when sourcing. */
  record ContentRow(String eventId, Set<String> tags, ObjectNode content) {}

  void save(List<ContentRow> rows);

  /** Returns content keyed by event id for every event carrying any of the given tags. */
  Map<String, ObjectNode> findByTags(Collection<String> tags);

  Optional<ObjectNode> findByEventId(String eventId);

  /**
   * Hard-erases the content of the given events: replaces it with an empty object and records when
   * it was erased. The rows are kept, so a missing row is never mistaken for erased content.
   * Content already erased is left unchanged.
   */
  void erase(Collection<String> eventIds);
}
