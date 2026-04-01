package uk.gov.justice.laa.dstew.access.repository;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/**
 * Repository for managing application entities.
 * Will be revisited when the application tables are merged in
 */
@Repository
public interface ApplicationSummaryRepository extends
    JpaRepository<ApplicationSummaryEntity, UUID>, JpaSpecificationExecutor<ApplicationSummaryEntity> {

  @Query(                                                                                                                                                                                                                 value = """
      SELECT ase FROM ApplicationSummaryEntity ase
      WHERE (:status IS NULL OR ase.status = :status)
      AND (:laaReference IS NULL OR LOWER(ase.laaReference) LIKE :laaReference)
      AND (:firstName IS NULL OR EXISTS (
          SELECT ind FROM ase.individuals ind
          WHERE LOWER(ind.firstName) LIKE :firstName AND ind.type = 'CLIENT'))
      AND (:lastName IS NULL OR EXISTS (
          SELECT ind FROM ase.individuals ind
          WHERE LOWER(ind.lastName) LIKE :lastName AND ind.type = 'CLIENT'))
      AND (CAST(:dateOfBirth AS java.time.LocalDate) IS NULL OR EXISTS (
          SELECT ind FROM ase.individuals ind
          WHERE ind.dateOfBirth = :dateOfBirth AND ind.type = 'CLIENT'))
      AND (CAST(:userId AS java.util.UUID) IS NULL OR ase.caseworker.id = :userId)
      AND (:matterType IS NULL OR ase.matterType = :matterType)
      AND (CAST(:isAutoGranted AS java.lang.Boolean) IS NULL OR ase.isAutoGranted = :isAutoGranted)
      """,
      countQuery = """
          SELECT COUNT(ase) FROM ApplicationSummaryEntity ase
          WHERE (:status IS NULL OR ase.status = :status)
          AND (:laaReference IS NULL OR LOWER(ase.laaReference) LIKE :laaReference)
          AND (:firstName IS NULL OR EXISTS (
              SELECT ind FROM ase.individuals ind
              WHERE LOWER(ind.firstName) LIKE :firstName AND ind.type = 'CLIENT'))
          AND (:lastName IS NULL OR EXISTS (
              SELECT ind FROM ase.individuals ind
              WHERE LOWER(ind.lastName) LIKE :lastName AND ind.type = 'CLIENT'))
          AND (CAST(:dateOfBirth AS java.time.LocalDate) IS NULL OR EXISTS (
              SELECT ind FROM ase.individuals ind
              WHERE ind.dateOfBirth = :dateOfBirth AND ind.type = 'CLIENT'))
          AND (CAST(:userId AS java.util.UUID) IS NULL OR ase.caseworker.id = :userId)
          AND (:matterType IS NULL OR ase.matterType = :matterType)
          AND (CAST(:isAutoGranted AS java.lang.Boolean) IS NULL OR ase.isAutoGranted = :isAutoGranted)
          """
  )
  Page<ApplicationSummaryEntity> findAllWithFilters(
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
}
