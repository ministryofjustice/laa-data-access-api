package uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;

/** Gateway interface for application persistence operations. */
public interface ApplicationGateway {

  /**
   * Saves a new application (INSERT only) and returns the saved domain with id + createdAt.
   *
   * @param domain the pre-save domain record
   * @return the domain enriched with database-generated fields
   */
  ApplicationDomain save(ApplicationDomain domain);

  /**
   * Returns {@code true} if an application with the given applyApplicationId already exists.
   *
   * @param applyApplicationId the Apply application ID to check
   * @return {@code true} if a duplicate exists, {@code false} otherwise
   */
  boolean existsByApplyApplicationId(UUID applyApplicationId);

  /**
   * Returns the lead application by its applyApplicationId, or empty if not found.
   *
   * @param applyApplicationId the Apply application ID to search for
   * @return an Optional containing the lead application domain, or empty
   */
  Optional<ApplicationDomain> findByLeadApplyApplicationId(UUID applyApplicationId);

  Optional<ApplicationDomain> findByApplicationId(UUID applicationId);

  /**
   * Returns any applyApplicationIds from the supplied list that do not exist in the repository. An
   * empty list means all IDs were found.
   *
   * @param applyApplicationIds the list of Apply application IDs to validate
   * @return a list of IDs that could not be found (empty if all were present)
   */
  List<UUID> findMissingApplyApplicationIds(List<UUID> applyApplicationIds);

  /**
   * Updates an existing application's status and content, then returns the saved domain.
   *
   * <p>Implementations load the managed entity, apply field changes, set modifiedAt to current
   * instant, and persist. This preserves optimistic locking semantics.
   *
   * @param id the application UUID
   * @param status the new status; if null, status remains unchanged
   * @param applicationContent the new application content
   * @return the updated application domain with modifiedAt set to current instant
   */
  ApplicationDomain update(UUID id, String status, Map<String, Object> applicationContent);
}
