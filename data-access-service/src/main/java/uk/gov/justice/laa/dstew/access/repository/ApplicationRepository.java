package uk.gov.justice.laa.dstew.access.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;

/** Repository for managing application entities. */
@Repository
public interface ApplicationRepository
    extends JpaRepository<ApplicationEntity, UUID>, ApplicationSummaryRepositoryCustom {
  ApplicationEntity findByApplyApplicationId(UUID applyApplicationId);

  boolean existsByApplyApplicationId(UUID applyApplicationId);

  List<ApplicationEntity> findAllByApplyApplicationIdIn(List<UUID> applyApplicationIds);

  @Query("SELECT a FROM ApplicationEntity a LEFT JOIN FETCH a.linkedApplications WHERE a.id = :id")
  Optional<ApplicationEntity> findByIdWithLinkedApplications(@Param("id") UUID id);

  /**
   * Finds all linked applications for the given page IDs in a single query. This combines the logic
   * of finding lead IDs and fetching all linked applications, reducing query count from 2 to 1.
   * Optimized to only query linked_applications table in CTE and use UNION ALL for better
   * performance.
   *
   * @param applicationPageIds the IDs of applications on the current page
   * @return list of linked application summary DTOs for all applications in the same link groups
   */
  @Query(
      value =
          "WITH lead_ids AS ( "
              + "  SELECT lead_application_id AS id "
              + "  FROM linked_applications "
              + "  WHERE lead_application_id IN :applicationPageIds "
              + "     OR associated_application_id IN :applicationPageIds "
              + ") "
              + "SELECT a.id, a.laa_reference, false AS is_lead, la.lead_application_id "
              + "FROM lead_ids lIds "
              + "JOIN linked_applications la ON la.lead_application_id = lIds.id "
              + "JOIN applications a ON a.id = la.associated_application_id "
              + "UNION ALL "
              + "SELECT a.id, a.laa_reference, true AS is_lead, a.id "
              + "FROM lead_ids lIds "
              + "JOIN applications a ON a.id = lIds.id",
      nativeQuery = true)
  List<LinkedApplicationSummaryDto> findAllLinkedApplicationsForPageIds(
      @Param("applicationPageIds") List<UUID> applicationPageIds);
}
