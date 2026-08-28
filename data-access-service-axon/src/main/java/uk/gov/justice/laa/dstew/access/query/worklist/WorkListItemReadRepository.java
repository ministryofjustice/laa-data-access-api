package uk.gov.justice.laa.dstew.access.query.worklist;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/** Repository for the replayable work-list tracking projection. */
public interface WorkListItemReadRepository
    extends JpaRepository<WorkListItemReadModel, WorkListItemId> {
  void deleteByIdItemTypeAndIdItemId(WorkItemType itemType, UUID itemId);
}

