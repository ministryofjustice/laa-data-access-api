package uk.gov.justice.laa.dstew.access.query.application.summary;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Looks up facts about applications from the summary. Read sides use this for display fields rather
 * than copying application content. The summary is built by its own processor, so a row can briefly
 * lag; a missing ID is simply absent from the result and callers do not wait.
 */
public interface ApplicationSummaries {

  /** Returns the summaries found for the given IDs, keyed by application ID. */
  Map<UUID, ApplicationSummaryReadModel> findByIds(Collection<UUID> applicationIds);

  /** Returns the summary for one application, if present. */
  default Optional<ApplicationSummaryReadModel> findById(UUID applicationId) {
    return Optional.ofNullable(findByIds(List.of(applicationId)).get(applicationId));
  }
}
