package uk.gov.justice.laa.dstew.access.massgenerator.generator;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes directly to the linked_individuals join table via a native query.
 *
 * <p>Kept in a separate bean so that @Transactional is honoured by the Spring proxy. Calling
 * a @Transactional method on the same bean that calls it (self-invocation) bypasses the proxy and
 * the transaction annotation is silently ignored.
 */
@Component("massGeneratorLinkedIndividualWriter")
public class LinkedIndividualWriter {

  @Autowired private EntityManager entityManager;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Transactional
  public void link(UUID applicationId, UUID individualId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO linked_individuals (application_id, individual_id) VALUES (:appId, :indivId)")
        .setParameter("appId", applicationId)
        .setParameter("indivId", individualId)
        .executeUpdate();
  }

  /**
   * Bulk-inserts a batch of (applicationId, individualId) pairs using JDBC batching. Should be
   * called after the Hibernate session has been flushed so the FK rows exist in the DB.
   */
  @Transactional
  public void linkAll(List<UUID[]> pairs) {
    if (pairs.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        "INSERT INTO linked_individuals (application_id, individual_id) VALUES (?, ?)",
        pairs,
        pairs.size(),
        (ps, pair) -> {
          ps.setObject(1, pair[0]);
          ps.setObject(2, pair[1]);
        });
  }
}
