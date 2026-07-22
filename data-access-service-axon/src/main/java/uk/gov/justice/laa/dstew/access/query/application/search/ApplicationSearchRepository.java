package uk.gov.justice.laa.dstew.access.query.application.search;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationSearchView;

@Repository
public interface ApplicationSearchRepository
    extends JpaRepository<ApplicationSearchView, UUID>,
        JpaSpecificationExecutor<ApplicationSearchView> {}
