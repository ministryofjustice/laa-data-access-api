package uk.gov.justice.laa.dstew.access.query.genesis;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for genesis stream pointers. */
public interface GenesisStreamRepository extends JpaRepository<GenesisStreamEntry, Long> {

  boolean existsByGenesisIdAndSourceAndEventIdentifier(
      UUID genesisId, String source, String eventIdentifier);

  /** Returns an application's pointers in the order its events happened. */
  List<GenesisStreamEntry> findAllByGenesisIdOrderByOccurredAtAscIdAsc(UUID genesisId);
}
