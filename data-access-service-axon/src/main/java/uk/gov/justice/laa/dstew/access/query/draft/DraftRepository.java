package uk.gov.justice.laa.dstew.access.query.draft;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the mutable drafts store. */
public interface DraftRepository extends JpaRepository<DraftRecord, UUID> {

  Optional<DraftRecord> findFirstByApplyApplicationIdOrderByCreatedAtDesc(UUID applyApplicationId);
}
