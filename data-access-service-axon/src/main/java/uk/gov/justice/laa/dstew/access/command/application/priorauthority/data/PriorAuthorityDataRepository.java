package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence interface for immutable versions of sensitive prior-authority data. */
public interface PriorAuthorityDataRepository
    extends JpaRepository<PriorAuthorityData, PriorAuthorityDataId> {}
