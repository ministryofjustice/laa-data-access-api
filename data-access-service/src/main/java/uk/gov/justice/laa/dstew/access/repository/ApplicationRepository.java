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
   * Uses a CTE to avoid duplicate subqueries and reduce parameter bindings.
   *
   * @param pageIds the IDs of applications on the current page
   * @return list of linked application summary DTOs for all applications in the same link groups
   */
  @Query(
      value =
          "WITH lead_ids AS ( "
              + "  SELECT id FROM applications "
              + "  WHERE id IN :pageIds "
              + "    AND EXISTS (SELECT 1 FROM linked_applications WHERE lead_application_id = id) "
              + "  UNION "
              + "  SELECT lead_application_id FROM linked_applications "
              + "  WHERE associated_application_id IN :pageIds "
              + ") "
              + "SELECT a.id, a.laa_reference, false AS is_lead, la.lead_application_id "
              + "FROM applications a "
              + "JOIN linked_applications la ON a.id = la.associated_application_id "
              + "WHERE la.lead_application_id IN (SELECT id FROM lead_ids) "
              + "UNION "
              + "SELECT a.id, a.laa_reference, true AS is_lead, a.id "
              + "FROM applications a "
              + "WHERE a.id IN (SELECT id FROM lead_ids)",
      nativeQuery = true)
  List<LinkedApplicationSummaryDto> findAllLinkedApplicationsForPageIds(
      @Param("pageIds") List<UUID> pageIds);
}
