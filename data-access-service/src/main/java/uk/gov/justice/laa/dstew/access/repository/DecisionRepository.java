package uk.gov.justice.laa.dstew.access.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;

/**
 * Repository for managing decision entities.
 */
@Repository
public interface DecisionRepository extends JpaRepository<DecisionEntity, UUID> {
    int countById(UUID id);
}
