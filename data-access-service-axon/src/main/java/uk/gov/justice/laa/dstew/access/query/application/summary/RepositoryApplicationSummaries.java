package uk.gov.justice.laa.dstew.access.query.application.summary;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Reads application summaries from the summary table in one batch. */
@Component
class RepositoryApplicationSummaries implements ApplicationSummaries {

  private final ApplicationSummaryReadRepository repository;

  RepositoryApplicationSummaries(ApplicationSummaryReadRepository repository) {
    this.repository = repository;
  }

  @Override
  public Map<UUID, ApplicationSummaryReadModel> findByIds(Collection<UUID> applicationIds) {
    if (applicationIds.isEmpty()) {
      return Map.of();
    }
    return repository.findAllById(applicationIds).stream()
        .collect(
            Collectors.toMap(ApplicationSummaryReadModel::getApplicationId, Function.identity()));
  }
}
