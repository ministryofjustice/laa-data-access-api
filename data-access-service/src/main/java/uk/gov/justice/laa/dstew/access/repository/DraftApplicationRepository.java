package uk.gov.justice.laa.dstew.access.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.DraftApplicationEntity;

/**
 * Repository for managing draft application entities.
 */
@Repository
public interface DraftApplicationRepository extends JpaRepository<DraftApplicationEntity, UUID> {
}