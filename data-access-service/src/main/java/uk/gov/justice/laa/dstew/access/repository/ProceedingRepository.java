package uk.gov.justice.laa.dstew.access.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;

/**
 * Repository for managing domain events entities.
 */
@Repository
public interface ProceedingRepository extends
        JpaRepository<ProceedingEntity, UUID> {

}
