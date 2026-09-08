package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA repository for reading prior-authority history rows by application. */
public interface PriorAuthorityHistoryReadRepository
    extends JpaRepository<PriorAuthorityHistoryReadModel, String> {

  List<PriorAuthorityHistoryReadModel> findAllByApplicationIdOrderByOccurredAtAsc(
      UUID applicationId);
}
