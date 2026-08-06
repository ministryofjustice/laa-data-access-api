package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for the linked application group current-state projection. */
public interface LinkedApplicationGroupReadRepository
    extends JpaRepository<LinkedApplicationGroupReadModel, UUID> {

  Optional<LinkedApplicationGroupReadModel> findByLeadApplicationId(UUID leadApplicationId);

  List<LinkedApplicationGroupReadModel> findAllByLeadApplicationIdIn(List<UUID> leadApplicationIds);

  @Query(
      value =
          "SELECT * FROM axon.linked_application_group_current_state"
              + " WHERE member_ids @> CAST('[\"' || CAST(:memberId AS TEXT) || '\"]' AS JSONB)",
      nativeQuery = true)
  Optional<LinkedApplicationGroupReadModel> findByMemberIdsContaining(
      @Param("memberId") UUID memberId);
}
