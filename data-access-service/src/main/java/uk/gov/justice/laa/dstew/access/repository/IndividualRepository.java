package uk.gov.justice.laa.dstew.access.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;

/**
 * Repository for accessing individual records.
 */
@Repository
public interface IndividualRepository extends JpaRepository<IndividualEntity, UUID> {
}