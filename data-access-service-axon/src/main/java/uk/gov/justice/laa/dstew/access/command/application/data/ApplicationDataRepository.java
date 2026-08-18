package uk.gov.justice.laa.dstew.access.command.application.data;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence interface for immutable versions of sensitive application data. */
public interface ApplicationDataRepository
    extends JpaRepository<ApplicationData, ApplicationDataId> {

  long countByIdApplicationId(UUID applicationId);
}
