package uk.gov.justice.laa.dstew.access.command.worklist.route;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for command-side route discovery only. */
public interface WorkItemRouteRepository extends JpaRepository<WorkItemRoute, UUID> {
  Optional<WorkItemRoute> findByWorkItemId(UUID workItemId);

  List<WorkItemRoute> findAllByGroupId(UUID groupId);
}

