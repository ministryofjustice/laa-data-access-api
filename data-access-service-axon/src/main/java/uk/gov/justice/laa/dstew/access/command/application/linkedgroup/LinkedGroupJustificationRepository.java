package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for authoritative linked-group justification payloads. */
public interface LinkedGroupJustificationRepository
    extends JpaRepository<LinkedGroupJustification, UUID> {}
