package uk.gov.justice.laa.dstew.access.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;

/**
 * Repository for managing domain events entities.
 */
@Repository
public interface DomainEventRepository extends
    JpaRepository<DomainEventEntity, UUID>, JpaSpecificationExecutor<DomainEventEntity> {

  List<DomainEventEntity> findAllByIsPublishedFalse();

  @Modifying(clearAutomatically = true)
  @Transactional
  @Query("UPDATE DomainEventEntity d SET d.isPublished = true WHERE d.id IN  :eventIds")
  int setIsPublishedTrueForEventId(@Param("eventIds") List<UUID> eventIds);
}
