package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence interface for the append-only Application history projection. */
public interface ApplicationHistoryReadRepository
    extends JpaRepository<ApplicationHistoryReadModel, String> {

  long countByApplicationId(UUID applicationId);

  List<ApplicationHistoryReadModel> findAllByApplicationIdOrderByOccurredAtAsc(UUID applicationId);
}
