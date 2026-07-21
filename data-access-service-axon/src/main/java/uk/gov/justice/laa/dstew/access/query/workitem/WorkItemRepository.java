package uk.gov.justice.laa.dstew.access.query.workitem;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository for the {@code work_items} caseworker-queue projection. */
public interface WorkItemRepository
    extends JpaRepository<WorkItemRecord, UUID>, JpaSpecificationExecutor<WorkItemRecord> {}
