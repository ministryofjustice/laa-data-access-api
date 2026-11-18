package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Stores sensitive text outside group events, within the accepted command transaction. */
@Component
@RequiredArgsConstructor
public class LinkedGroupJustificationStore {
  private final LinkedGroupJustificationRepository repository;

  /** Persists supplied text only; an omitted justification has no payload reference. */
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID save(UUID groupId, String justification, Instant occurredAt) {
    if (justification == null) {
      return null;
    }
    UUID id = UUID.randomUUID();
    repository.saveAndFlush(new LinkedGroupJustification(id, groupId, justification, occurredAt));
    return id;
  }

  /** Loads all referenced payloads in one query without interpreting missing data. */
  public Map<UUID, String> findAll(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return repository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                LinkedGroupJustification::getId, LinkedGroupJustification::getJustification));
  }
}
