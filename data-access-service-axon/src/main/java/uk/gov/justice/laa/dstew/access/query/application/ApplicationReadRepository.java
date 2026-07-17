package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence interface for the current Application projection. */
public interface ApplicationReadRepository
    extends JpaRepository<ApplicationReadModel, UUID>,
        JpaSpecificationExecutor<ApplicationReadModel> {}
