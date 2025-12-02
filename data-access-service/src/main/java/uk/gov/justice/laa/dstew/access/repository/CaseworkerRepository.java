package uk.gov.justice.laa.dstew.access.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;

/**
 * Repository for managing caseworker entities.
 */
@Repository
public interface CaseworkerRepository extends JpaRepository<CaseworkerEntity, UUID>  {
  long countById(UUID id);
}
