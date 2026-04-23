package uk.gov.justice.laa.dstew.access.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntityV2;

/** Repository for managing ApplicationEntityV2 — used by the aggregate-root clean-arch path. */
@Repository
public interface ApplicationRepositoryV2 extends JpaRepository<ApplicationEntityV2, UUID> {

  ApplicationEntityV2 findByApplyApplicationId(UUID applyApplicationId);

  boolean existsByApplyApplicationId(UUID applyApplicationId);

  List<ApplicationEntityV2> findAllByApplyApplicationIdIn(List<UUID> applyApplicationIds);
}
