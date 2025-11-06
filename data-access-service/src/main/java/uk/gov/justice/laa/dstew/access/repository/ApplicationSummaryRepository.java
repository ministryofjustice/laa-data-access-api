package uk.gov.justice.laa.dstew.access.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;

/**
 * Repository for managing application entities.
 * Will be revisited when the application tables are merged in
 */
@Repository
public interface ApplicationSummaryRepository extends JpaRepository<ApplicationSummaryEntity, UUID> {
    List<ApplicationSummaryEntity> findByStatusCodeLookupEntity_Code(String status, Pageable pageable);

    Integer countByStatusCodeLookupEntity_Code(String status);
}
