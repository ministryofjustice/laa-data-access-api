package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence interface for immutable versions of sensitive prior-authority data. */
public interface PriorAuthorityDataRepository
    extends JpaRepository<PriorAuthorityData, PriorAuthorityDataId> {

  @Query(
      "SELECT p FROM PriorAuthorityData p WHERE p.id.priorAuthorityId = :priorAuthorityId"
          + " ORDER BY p.id.dataVersion ASC LIMIT 1")
  Optional<PriorAuthorityData> findFirstByPriorAuthorityId(
      @Param("priorAuthorityId") UUID priorAuthorityId);
}
