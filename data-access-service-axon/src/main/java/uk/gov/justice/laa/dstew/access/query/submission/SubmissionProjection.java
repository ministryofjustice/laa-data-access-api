package uk.gov.justice.laa.dstew.access.query.submission;

import java.util.Optional;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

/**
 * Read side for the submitted payload stored in the {@code submissions} table.
 *
 * <p>The table is written by the application layer (see {@code SubmitApplicationService}) as the
 * system-of-record for the submitted body, so this component only <em>queries</em> it — it holds no
 * event handler and is deliberately not reset on replay: the body is not derivable from the slim
 * pointer events.
 */
@Component
public class SubmissionProjection {

  private final SubmissionRepository repository;

  public SubmissionProjection(SubmissionRepository repository) {
    this.repository = repository;
  }

  /** Returns the raw submission payload for the requested application. */
  @QueryHandler
  public Optional<SubmissionData> handle(FindSubmissionByApplicationIdQuery query) {
    return repository
        .findFirstByApplyApplicationIdOrderByCreatedAtDesc(query.applyApplicationId())
        .map(SubmissionRecord::getData);
  }
}
