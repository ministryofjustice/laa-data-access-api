package uk.gov.justice.laa.dstew.access.command.application.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence interface for document filenames. */
public interface DocumentFilenameRepository extends JpaRepository<DocumentFilename, UUID> {

  Optional<DocumentFilename> findByOwnerIdAndDocumentId(UUID ownerId, UUID documentId);
}
