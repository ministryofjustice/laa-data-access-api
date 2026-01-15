package uk.gov.justice.laa.dstew.access.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;

/**
 * Repository for managing domain events entities.
 */
@Repository
public interface DomainEventRepository extends
    JpaRepository<DomainEventEntity, UUID>, JpaSpecificationExecutor<DomainEventEntity> {
}
