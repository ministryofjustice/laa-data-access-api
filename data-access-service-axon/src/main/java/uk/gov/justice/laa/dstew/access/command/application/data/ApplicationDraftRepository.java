package uk.gov.justice.laa.dstew.access.command.application.data;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence interface for mutable Application draft content. */
public interface ApplicationDraftRepository extends JpaRepository<ApplicationDraft, UUID> {}
