package uk.gov.justice.laa.dstew.access.query.priorauthority;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the deletable, mutable prior authority draft store. */
public interface PriorAuthorityDraftRepository
    extends JpaRepository<PriorAuthorityDraftRecord, UUID> {}
