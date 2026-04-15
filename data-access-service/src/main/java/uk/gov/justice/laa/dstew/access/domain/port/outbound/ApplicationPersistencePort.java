package uk.gov.justice.laa.dstew.access.domain.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.model.Application;

/** Driven port (outbound) for persisting and querying applications. */
public interface ApplicationPersistencePort {

  /**
   * Persists an application and returns the saved instance (with generated fields populated).
   *
   * @param application the domain application to save
   * @return the saved application
   */
  Application save(Application application);

  /**
   * Checks whether an application with the given apply-application ID already exists.
   *
   * @param applyApplicationId the apply-application ID to check
   * @return {@code true} if a matching application exists
   */
  boolean existsByApplyApplicationId(UUID applyApplicationId);

  /**
   * Finds an application by its apply-application ID.
   *
   * @param applyApplicationId the apply-application ID
   * @return an optional containing the matching application, or empty if not found
   */
  Optional<Application> findByApplyApplicationId(UUID applyApplicationId);

  /**
   * Finds all applications whose apply-application IDs are in the given list.
   *
   * @param applyApplicationIds the list of apply-application IDs
   * @return matching applications
   */
  List<Application> findAllByApplyApplicationIdIn(List<UUID> applyApplicationIds);
}
