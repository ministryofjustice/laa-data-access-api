package uk.gov.justice.laa.dstew.access.query.synchronousapplication;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the SynchronousApplication current-state projection. */
public interface SynchronousApplicationReadRepository
    extends JpaRepository<SynchronousApplicationReadModel, UUID> {}
