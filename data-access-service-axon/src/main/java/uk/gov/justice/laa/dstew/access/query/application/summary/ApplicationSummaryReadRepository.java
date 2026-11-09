package uk.gov.justice.laa.dstew.access.query.application.summary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;

/** Persistence interface for the {@code application_summary} projection. */
public interface ApplicationSummaryReadRepository
    extends JpaRepository<ApplicationSummaryReadModel, UUID>,
        JpaSpecificationExecutor<ApplicationSummaryReadModel> {

  List<ApplicationSummaryReadModel> findAllByLinkedGroupId(UUID linkedGroupId);

  List<ApplicationSummaryReadModel> findAllByStatusAndAutoGrantedAndSubmittedAtBefore(
      String status, AutoGrantedState autoGranted, Instant submittedBefore);
}
