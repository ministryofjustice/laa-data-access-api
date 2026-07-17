package uk.gov.justice.laa.dstew.access.query.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubmissionProjectionTest {

  private final SubmissionRepository repository = mock(SubmissionRepository.class);
  private final SubmissionProjection projection = new SubmissionProjection(repository);

  @Test
  void givenStoredSubmission_whenQueried_thenReturnsPayload() {
    UUID applyApplicationId = UUID.randomUUID();
    SubmissionData data = new SubmissionData(null, List.of(), List.of());
    SubmissionRecord record = SubmissionRecord.builder().data(data).build();
    when(repository.findFirstByApplyApplicationIdOrderByCreatedAtDesc(applyApplicationId))
        .thenReturn(Optional.of(record));

    Optional<SubmissionData> result =
        projection.handle(new FindSubmissionByApplicationIdQuery(applyApplicationId));

    assertThat(result).contains(data);
  }

  @Test
  void givenNoSubmission_whenQueried_thenReturnsEmpty() {
    UUID applyApplicationId = UUID.randomUUID();
    when(repository.findFirstByApplyApplicationIdOrderByCreatedAtDesc(applyApplicationId))
        .thenReturn(Optional.empty());

    Optional<SubmissionData> result =
        projection.handle(new FindSubmissionByApplicationIdQuery(applyApplicationId));

    assertThat(result).isEmpty();
  }
}
