package uk.gov.justice.laa.dstew.access.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;


/**
 * Repository for managing application entities.
 */
@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {
  ApplicationEntity findByApplyApplicationId(UUID applyApplicationId);

  List<ApplicationEntity> findAllByApplyApplicationIdIn(List<UUID> applyApplicationIds);

  @Query("SELECT la.leadApplicationId FROM LinkedApplicationEntity la WHERE la.associatedApplicationId IN :pageIds")
  List<UUID> findLeadIdsByAssociatedIds(@Param("pageIds") List<UUID> pageIds);

  @Query("SELECT new uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto("
      + "a.id, a.laaReference, false, la.leadApplicationId) "
      + "FROM ApplicationEntity a "
      + "JOIN LinkedApplicationEntity la ON a.id = la.associatedApplicationId "
      + "WHERE la.leadApplicationId IN :leadIds "
      + "UNION "
      + "SELECT new uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto("
      + "a.id, a.laaReference, true, a.id) "
      + "FROM ApplicationEntity a "
      + "WHERE a.id IN :leadIds")
  List<LinkedApplicationSummaryDto> findAllLinkedApplicationsByLeadIds(@Param("leadIds") List<UUID> leadIds);

}

