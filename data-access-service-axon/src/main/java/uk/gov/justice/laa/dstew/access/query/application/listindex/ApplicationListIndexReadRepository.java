package uk.gov.justice.laa.dstew.access.query.application.listindex;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence interface for the {@code application_list_index} projection. */
public interface ApplicationListIndexReadRepository
    extends JpaRepository<ApplicationListIndexReadModel, UUID>,
        JpaSpecificationExecutor<ApplicationListIndexReadModel> {}
