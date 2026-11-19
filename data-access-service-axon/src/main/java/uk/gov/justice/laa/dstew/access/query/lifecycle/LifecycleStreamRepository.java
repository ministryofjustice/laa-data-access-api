package uk.gov.justice.laa.dstew.access.query.lifecycle;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for lifecycle stream pointers. */
public interface LifecycleStreamRepository extends JpaRepository<LifecycleStreamEntry, Long> {

  boolean existsByGenesisIdAndSourceAndEventIdentifier(
      UUID genesisId, String source, String eventIdentifier);

  /** Returns an application's pointers in the order its events happened. */
  List<LifecycleStreamEntry> findAllByGenesisIdOrderByOccurredAtAscIdAsc(UUID genesisId);
}
