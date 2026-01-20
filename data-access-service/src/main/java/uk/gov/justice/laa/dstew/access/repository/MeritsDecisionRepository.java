package uk.gov.justice.laa.dstew.access.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;

/**
 * Repository for managing merits decision entities.
 */
@Repository
public interface MeritsDecisionRepository extends JpaRepository<MeritsDecisionEntity, UUID> {
}
