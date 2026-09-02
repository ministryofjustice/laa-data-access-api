package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence interface for mutable prior-authority draft content. */
public interface PriorAuthorityDraftRepository extends JpaRepository<PriorAuthorityDraft, UUID> {}
