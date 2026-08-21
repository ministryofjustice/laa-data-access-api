package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence interface for the current prior-authority projection. */
public interface PriorAuthorityReadRepository
    extends JpaRepository<PriorAuthorityReadModel, UUID> {}
