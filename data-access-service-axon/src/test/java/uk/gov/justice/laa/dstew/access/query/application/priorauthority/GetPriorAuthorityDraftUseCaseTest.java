package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.PriorAuthorityType;

class GetPriorAuthorityDraftUseCaseTest {

  private PriorAuthorityDraftStore draftStore;
  private GetPriorAuthorityDraftUseCase useCase;

  @BeforeEach
  void setUp() {
    draftStore = mock(PriorAuthorityDraftStore.class);
    useCase = new GetPriorAuthorityDraftUseCase(draftStore);
  }

  @Test
  void givenExistingDraft_whenRetrieved_thenReturnsResultWithInProgressStatus() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            "EXPERT",
            "Expert is required",
            new ExpertDetails("PSYCHIATRIST", "Jane Doe", "AB1 2CD", null),
            null,
            null);
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            submissionId, applicationId, content, "{}", Instant.parse("2026-08-26T10:00:00Z"));
    when(draftStore.find(submissionId)).thenReturn(Optional.of(payload));

    PriorAuthorityResult result = useCase.getPriorAuthorityDraft(submissionId);

    assertThat(result.priorAuthorityId()).isEqualTo(submissionId);
    assertThat(result.applicationId()).isEqualTo(applicationId);
    assertThat(result.status()).isEqualTo("IN_PROGRESS");
    assertThat(result.priorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT);
    assertThat(result.expertDetails()).isNotNull();
    assertThat(result.expertDetails().expertFullName()).isEqualTo("Jane Doe");
  }

  @Test
  void givenNoDraft_whenRetrieved_thenThrowsNotFound() {
    UUID submissionId = UUID.randomUUID();
    when(draftStore.find(submissionId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.getPriorAuthorityDraft(submissionId))
        .withMessage("No prior authority draft found with ID: " + submissionId);
  }
}
