package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence interface for the current Application projection. */
public interface ApplicationReadRepository extends JpaRepository<ApplicationReadModel, UUID> {}
