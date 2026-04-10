package uk.gov.justice.laa.dstew.access.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.ClientIndividualDto;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.MatterType;


/**
 * Repository for managing application entities.
 */
@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {
  ApplicationEntity findByApplyApplicationId(UUID applyApplicationId);

  boolean existsByApplyApplicationId(UUID applyApplicationId);

  List<ApplicationEntity> findAllByApplyApplicationIdIn(List<UUID> applyApplicationIds);

  @Query(
      value = "SELECT DISTINCT la.lead_application_id "
          + "FROM linked_applications la "
          + "WHERE la.associated_application_id IN :pageIds OR la.lead_application_id IN :pageIds",
      nativeQuery = true)
  List<UUID> findLeadIdsByPageIds(@Param("pageIds") List<UUID> pageIds);

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

  @Query("""
      SELECT a FROM ApplicationEntity a
      LEFT JOIN FETCH a.caseworker
      LEFT JOIN FETCH a.decision
      LEFT JOIN FETCH a.linkedApplications
      WHERE a.id = :id
      """)
  Optional<ApplicationEntity> findByIdWithAssociations(@Param("id") UUID id);

  @Query(
      value = """
      SELECT new uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto(
        ase.id,
        ase.status,
        ase.laaReference,
        ase.officeCode,
        ase.submittedAt,
        ase.modifiedAt,
        ase.usedDelegatedFunctions,
        ase.categoryOfLaw,
        ase.matterType,
        ase.isAutoGranted,
        CASE WHEN EXISTS (SELECT 1 FROM ApplicationEntity linked 
              WHERE linked MEMBER OF ase.linkedApplications) THEN true ELSE false END,
        ase.caseworker.id
      )
      FROM ApplicationEntity ase
      WHERE (:status IS NULL OR ase.status = :status)
      AND (:laaReference IS NULL OR LOWER(ase.laaReference) LIKE :laaReference)
      AND (:firstName IS NULL OR EXISTS (
          SELECT 1 FROM ase.individuals ind
          WHERE LOWER(ind.firstName) LIKE :firstName AND ind.type = 'CLIENT'))
      AND (:lastName IS NULL OR EXISTS (
          SELECT 1 FROM ase.individuals ind
          WHERE LOWER(ind.lastName) LIKE :lastName AND ind.type = 'CLIENT'))
      AND (CAST(:dateOfBirth AS java.time.LocalDate) IS NULL OR EXISTS (
          SELECT 1 FROM ase.individuals ind
          WHERE ind.dateOfBirth = :dateOfBirth AND ind.type = 'CLIENT'))
      AND (CAST(:userId AS java.util.UUID) IS NULL OR ase.caseworker.id = :userId)
      AND (:matterType IS NULL OR ase.matterType = :matterType)
      AND (CAST(:isAutoGranted AS java.lang.Boolean) IS NULL OR ase.isAutoGranted = :isAutoGranted)
      """,
      countQuery = """
          SELECT COUNT(DISTINCT ase) FROM ApplicationEntity ase
          WHERE (:status IS NULL OR ase.status = :status)
          AND (:laaReference IS NULL OR LOWER(ase.laaReference) LIKE :laaReference)
          AND (:firstName IS NULL OR EXISTS (
              SELECT 1 FROM ase.individuals ind
              WHERE LOWER(ind.firstName) LIKE :firstName AND ind.type = 'CLIENT'))
          AND (:lastName IS NULL OR EXISTS (
              SELECT 1 FROM ase.individuals ind
              WHERE LOWER(ind.lastName) LIKE :lastName AND ind.type = 'CLIENT'))
          AND (CAST(:dateOfBirth AS java.time.LocalDate) IS NULL OR EXISTS (
              SELECT 1 FROM ase.individuals ind
              WHERE ind.dateOfBirth = :dateOfBirth AND ind.type = 'CLIENT'))
          AND (CAST(:userId AS java.util.UUID) IS NULL OR ase.caseworker.id = :userId)
          AND (:matterType IS NULL OR ase.matterType = :matterType)
          AND (CAST(:isAutoGranted AS java.lang.Boolean) IS NULL OR ase.isAutoGranted = :isAutoGranted)
          """
  )
  Page<ApplicationSummaryDto> findAllWithFilters(
      @Param("status") ApplicationStatus status,
      @Param("laaReference") String laaReference,
      @Param("firstName") String firstName,
      @Param("lastName") String lastName,
      @Param("dateOfBirth") LocalDate dateOfBirth,
      @Param("userId") UUID userId,
      @Param("matterType") MatterType matterType,
      @Param("isAutoGranted") Boolean isAutoGranted,
      Pageable pageable
  );

  @Query("""
      SELECT new uk.gov.justice.laa.dstew.access.model.ClientIndividualDto(
        a.id,
        ind.firstName,
        ind.lastName,
        ind.dateOfBirth
      )
      FROM ApplicationEntity a
      JOIN a.individuals ind
      WHERE a.id IN :applicationIds
      AND ind.type = 'CLIENT'
      """)
  List<ClientIndividualDto> findClientIndividualsByApplicationIds(@Param("applicationIds") List<UUID> applicationIds);
}

