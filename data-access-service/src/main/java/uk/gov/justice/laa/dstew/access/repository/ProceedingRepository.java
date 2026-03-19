package uk.gov.justice.laa.dstew.access.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import java.util.UUID;

/**
 * Repository for managing proceeding entities.
 */
@Repository
public interface ProceedingRepository extends
        JpaRepository<ProceedingEntity, UUID> {
}
