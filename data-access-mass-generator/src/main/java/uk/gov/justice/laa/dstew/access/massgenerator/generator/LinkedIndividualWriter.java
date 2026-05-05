package uk.gov.justice.laa.dstew.access.massgenerator.generator;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Writes directly to the linked_individuals join table via a native query.
 *
 * <p>Kept in a separate bean so that @Transactional is honoured by the Spring proxy. Calling
 * a @Transactional method on the same bean that calls it (self-invocation) bypasses the proxy and
 * the transaction annotation is silently ignored.
 */
@Component
public class LinkedIndividualWriter {

  private static final int LINK_CHUNK_SIZE = 200;

  @Autowired private EntityManager entityManager;

  private final List<UUID[]> deferredLinks = new ArrayList<>();

  @Transactional
  public void link(UUID applicationId, UUID individualId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO linked_individuals (application_id, individual_id) VALUES (:appId, :indivId)")
        .setParameter("appId", applicationId)
        .setParameter("indivId", individualId)
        .executeUpdate();
  }

  /** Queue a link for batch insertion. Call flushDeferred() at batch boundaries. */
  public void linkDeferred(UUID applicationId, UUID individualId) {
    deferredLinks.add(new UUID[] {applicationId, individualId});
  }

  /** Flush all deferred links in chunked multi-row INSERTs to avoid oversized SQL statements. */
  @Transactional
  public void flushDeferred() {
    if (deferredLinks.isEmpty()) {
      return;
    }

    for (int chunkStart = 0; chunkStart < deferredLinks.size(); chunkStart += LINK_CHUNK_SIZE) {
      int chunkEnd = Math.min(chunkStart + LINK_CHUNK_SIZE, deferredLinks.size());
      List<UUID[]> chunk = deferredLinks.subList(chunkStart, chunkEnd);

      StringBuilder sql =
          new StringBuilder(
              "INSERT INTO linked_individuals (application_id, individual_id) VALUES ");
      for (int i = 0; i < chunk.size(); i++) {
        if (i > 0) {
          sql.append(",");
        }
        sql.append("(?").append(i * 2 + 1).append(",?").append(i * 2 + 2).append(")");
      }

      var query = entityManager.createNativeQuery(sql.toString());
      for (int i = 0; i < chunk.size(); i++) {
        UUID[] pair = chunk.get(i);
        query.setParameter(i * 2 + 1, pair[0]);
        query.setParameter(i * 2 + 2, pair[1]);
      }
      query.executeUpdate();
    }

    deferredLinks.clear();
  }
}
