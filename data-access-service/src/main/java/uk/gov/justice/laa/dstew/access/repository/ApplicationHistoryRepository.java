package uk.gov.justice.laa.dstew.access.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ApplicationHistoryEntity;

/**
 * Repository for managing application entities.
 */
@Repository
public interface ApplicationHistoryRepository
    extends JpaRepository<ApplicationHistoryEntity, UUID> {

  Optional<ApplicationHistoryEntity> findFirstByApplicationIdOrderByTimestampDesc(UUID applicationId);

  List<ApplicationHistoryEntity> findByApplicationId(UUID applicationId);

}