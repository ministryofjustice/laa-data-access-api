package uk.gov.justice.laa.dstew.access.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;

/**
 * Repository for managing linked application entities.
 */
@Repository
public interface LinkedApplicationRepository
    extends JpaRepository<LinkedApplicationEntity, UUID> {

  boolean existsByLeadApplicationIdAndAssociatedApplicationId(
      UUID leadApplicationId,
      UUID associatedApplicationId
  );
}

