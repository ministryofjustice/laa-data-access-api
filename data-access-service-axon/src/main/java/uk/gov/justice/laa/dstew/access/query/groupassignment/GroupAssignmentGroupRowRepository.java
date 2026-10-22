package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Base repository for one projection's tracked group rows. */
@NoRepositoryBean
public interface GroupAssignmentGroupRowRepository<T extends GroupAssignmentGroupRow>
    extends JpaRepository<T, UUID> {}
