package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence interface for uploaded documents metadata. */
public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, UUID> {}
