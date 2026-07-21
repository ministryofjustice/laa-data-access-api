package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the Application current-state projection. */
public interface ApplicationReadRepository extends JpaRepository<ApplicationReadModel, UUID> {}
