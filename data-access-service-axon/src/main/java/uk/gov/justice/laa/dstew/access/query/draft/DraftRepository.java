package uk.gov.justice.laa.dstew.access.query.draft;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the mutable application drafts store, keyed by application id. */
public interface DraftRepository extends JpaRepository<DraftRecord, UUID> {}
