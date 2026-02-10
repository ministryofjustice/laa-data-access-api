package uk.gov.justice.laa.dstew.access.repository;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/**
 * Repository for managing application entities.
 */
@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

  @Query("""
      SELECT a FROM ApplicationSummaryEntity a
      WHERE (CAST(:status AS string) IS NULL OR a.status = :status)
        AND (CAST(:reference AS string) IS NULL OR LOWER(a.laaReference) LIKE LOWER(CONCAT('%', :reference, '%')))
        AND (CAST(:userId AS string) IS NULL OR a.caseworker.id = :userId)
        AND (CAST(:matterType AS string) IS NULL OR a.matterType = :matterType)
        AND (CAST(:isAutoGranted AS string) IS NULL OR a.isAutoGranted = :isAutoGranted)
        AND (CAST(:firstName AS string) IS NULL OR EXISTS (
            SELECT 1 FROM a.individuals i WHERE i.type = 'CLIENT'
            AND LOWER(i.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))))
        AND (CAST(:lastName AS string) IS NULL OR EXISTS (
            SELECT 1 FROM a.individuals i WHERE i.type = 'CLIENT'
            AND LOWER(i.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))))
        AND (CAST(:dob AS date) IS NULL OR EXISTS (
            SELECT 1 FROM a.individuals i WHERE i.type = 'CLIENT'
            AND i.dateOfBirth = :dob))
      """)
  Page<ApplicationSummaryEntity> findApplicationSummaries(
      @Param("status") ApplicationStatus status,
      @Param("reference") String reference,
      @Param("firstName") String firstName,
      @Param("lastName") String lastName,
      @Param("dob") LocalDate dob,
      @Param("userId") UUID userId,
      @Param("matterType") MatterType matterType,
      @Param("isAutoGranted") Boolean isAutoGranted,
      Pageable pageable);
}
