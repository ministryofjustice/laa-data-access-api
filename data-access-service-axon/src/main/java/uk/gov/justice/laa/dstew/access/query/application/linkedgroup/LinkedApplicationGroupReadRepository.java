package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the linked application group current-state projection. */
public interface LinkedApplicationGroupReadRepository
    extends JpaRepository<LinkedApplicationGroupReadModel, UUID> {}
