package uk.gov.justice.laa.dstew.access.query.worklist;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository for the replayable work-list tracking projection. */
public interface WorkListItemReadRepository
    extends JpaRepository<WorkListItemReadModel, UUID>,
        JpaSpecificationExecutor<WorkListItemReadModel> {}
