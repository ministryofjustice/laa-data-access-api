package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the linked application group current-state projection. */
public interface LinkedApplicationGroupReadRepository
    extends JpaRepository<LinkedApplicationGroupReadModel, UUID> {

  Optional<LinkedApplicationGroupReadModel> findByLeadApplicationId(UUID leadApplicationId);

  List<LinkedApplicationGroupReadModel> findAllByLeadApplicationIdIn(List<UUID> leadApplicationIds);
}
