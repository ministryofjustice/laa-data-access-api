package uk.gov.justice.laa.dstew.access.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ClientIndividualDto;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;


/**
 * Repository for managing application entities.
 */
@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID>,
    JpaSpecificationExecutor<ApplicationEntity> {
  ApplicationEntity findByApplyApplicationId(UUID applyApplicationId);

  boolean existsByApplyApplicationId(UUID applyApplicationId);

  List<ApplicationEntity> findAllByApplyApplicationIdIn(List<UUID> applyApplicationIds);

  @Query(
      value = "SELECT DISTINCT la.lead_application_id "
          + "FROM linked_applications la "
          + "WHERE la.associated_application_id IN :pageIds OR la.lead_application_id IN :pageIds",
      nativeQuery = true)
  List<UUID> findLeadIdsByAssociatedIds(@Param("pageIds") List<UUID> pageIds);

  @Query(
      value = "SELECT a.id, a.laa_reference, false AS is_lead, la.lead_application_id "
          + "FROM applications a "
          + "JOIN linked_applications la ON a.id = la.associated_application_id "
          + "WHERE la.lead_application_id IN :leadIds "
          + "UNION "
          + "SELECT a.id, a.laa_reference, true AS is_lead, a.id "
          + "FROM applications a "
          + "WHERE a.id IN :leadIds",
      nativeQuery = true)
  List<LinkedApplicationSummaryDto> findAllLinkedApplicationsByLeadIds(@Param("leadIds") List<UUID> leadIds);

  @Query(
      value = "SELECT li.application_id AS applicationId, "
          + "i.first_name AS firstName, "
          + "i.last_name AS lastName, "
          + "i.date_of_birth AS dateOfBirth "
          + "FROM linked_individuals li "
          + "JOIN individuals i ON li.individual_id = i.id "
          + "WHERE li.application_id IN :applicationIds "
          + "AND i.individual_type = 'CLIENT'",
      nativeQuery = true)
  List<ClientIndividualDto> findClientIndividualsByApplicationIds(@Param("applicationIds") List<UUID> applicationIds);
}

