package uk.gov.justice.laa.dstew.access.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;

/**
 * Manages the persistence of notes for an application.
 *
 */
@Repository
public interface NoteRepository extends JpaRepository<NoteEntity, UUID> {
  List<NoteEntity> findByApplicationId(UUID applicationId);

  List<NoteEntity> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
