package uk.gov.justice.laa.dstew.access.query.worklist;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the work-list projection's linked-group membership lookup. */
public interface WorkListGroupMemberReadRepository
    extends JpaRepository<WorkListGroupMemberReadModel, UUID> {
  List<WorkListGroupMemberReadModel> findAllByGroupId(UUID groupId);
}
