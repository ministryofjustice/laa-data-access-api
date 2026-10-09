package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence interface for the current prior-authority projection. */
public interface PriorAuthorityReadRepository
    extends JpaRepository<PriorAuthorityReadModel, UUID>,
        JpaSpecificationExecutor<PriorAuthorityReadModel> {

  /** Returns all prior-authority current-state rows linked to the given application IDs. */
  List<PriorAuthorityReadModel> findAllByApplicationIdIn(Collection<UUID> applicationIds);
}
