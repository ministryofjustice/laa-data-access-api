package uk.gov.justice.laa.dstew.access.query.submission;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the submissions payload store. */
public interface SubmissionRepository extends JpaRepository<SubmissionRecord, UUID> {

  Optional<SubmissionRecord> findFirstByApplyApplicationIdOrderByCreatedAtDesc(
      UUID applyApplicationId);
}
