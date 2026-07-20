package uk.gov.justice.laa.dstew.access.command.application.data;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Repository for granular PII fragment storage, keyed by per-section ref UUIDs. */
@Repository
public interface ApplicationPiiRepository {

  /** Persists a single PII fragment keyed by its ref UUID. Idempotent on same fragmentRef. */
  void saveFragment(
      UUID applicationId,
      UUID fragmentRef,
      String sectionName,
      byte[] content,
      Instant savedAt);

  /**
   * Returns the most recent non-redacted fragment for this applicationId and section, or empty if
   * none exists.
   */
  Optional<ExistingFragment> findLatestFragment(UUID applicationId, String sectionName);

  /**
   * Resolves a batch of fragment refs to their stored content bytes. Missing or REDACTED refs are
   * absent from the returned map.
   */
  Map<UUID, byte[]> findFragments(Set<UUID> fragmentRefs);

  /**
   * Marks all fragments for the given applicationId as REDACTED. Preserves rows for audit trail.
   */
  void redactAllForApplication(UUID applicationId, String reason, String actor);

  /** Value type returned by findLatestFragment. */
  record ExistingFragment(UUID ref, String contentHash) {}
}
