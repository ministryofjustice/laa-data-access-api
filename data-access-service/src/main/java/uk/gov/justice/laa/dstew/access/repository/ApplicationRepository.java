package uk.gov.justice.laa.dstew.access.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;



/**
 * Repository for managing application entities.
 */
@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {
  ApplicationEntity findByApplyApplicationId(UUID applyApplicationId);

  @Query(value = "SELECT LA.lead_application_id, LA.associated_application_id, APP.laa_reference, false "
                  + "FROM linked_applications AS LA INNER JOIN applications AS APP "
                  + "ON APP.id = LA.associated_application_id WHERE LA.lead_application_id IN ?1", nativeQuery = true)
  List<LinkedApplicationSummaryDto> findAssociateApplications(UUID[] ids);
}

