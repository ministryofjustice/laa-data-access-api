package uk.gov.justice.laa.dstew.access.command.worklist.route;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/** Repository for command-side route discovery only. */
public interface WorkItemRouteRepository extends JpaRepository<WorkItemRoute, WorkItemRouteId> {
  Optional<WorkItemRoute> findByIdWorkItemTypeAndIdWorkItemId(
      WorkItemType workItemType, UUID workItemId);
}

