package uk.gov.justice.laa.dstew.access.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;

/**
 * Repository for managing Certificate entities.
 */
@Repository
public interface CertificateRepository extends JpaRepository<CertificateEntity, UUID> {
  void deleteByApplicationId(UUID applicationId);

  boolean existsByApplicationId(UUID applicationId);
}
