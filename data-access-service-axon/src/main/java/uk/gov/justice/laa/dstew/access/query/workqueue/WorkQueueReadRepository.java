package uk.gov.justice.laa.dstew.access.query.workqueue;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the work queue current-state projection. */
public interface WorkQueueReadRepository extends JpaRepository<WorkQueueReadModel, UUID> {

  /** Returns a page of items with no assigned caseworker, oldest submitted first. */
  Page<WorkQueueReadModel> findByAssignedToIsNullOrderBySubmittedAtAsc(Pageable pageable);

  /** Returns a page of items assigned to the given caseworker, oldest submitted first. */
  Page<WorkQueueReadModel> findByAssignedToOrderBySubmittedAtAsc(UUID assignedTo, Pageable pageable);
}
