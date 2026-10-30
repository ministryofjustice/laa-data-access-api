package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Base repository for one projection's tracked application rows. */
@NoRepositoryBean
public interface GroupAssignmentApplicationRowRepository<T extends GroupAssignmentApplicationRow>
    extends JpaRepository<T, UUID> {
  List<T> findAllByGroupIdOrderByApplicationId(UUID groupId);
}
