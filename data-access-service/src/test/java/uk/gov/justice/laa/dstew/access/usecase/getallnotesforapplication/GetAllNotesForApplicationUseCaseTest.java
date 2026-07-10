package uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.domain.NoteReadModel;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.infrastructure.GetAllNotesForApplicationNoteGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getallnotesforapplication.NoteReadModelGenerator;

@ExtendWith(MockitoExtension.class)
class GetAllNotesForApplicationUseCaseTest {

  @Mock private ApplicationGateway applicationGateway;
  @Mock private GetAllNotesForApplicationNoteGateway noteGateway;

  private GetAllNotesForApplicationUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetAllNotesForApplicationUseCase(applicationGateway, noteGateway);
  }

  @Test
  void givenApplicationExistsWithNotes_whenExecuted_thenReturnsNotesInOrder() {
    UUID applicationId = UUID.randomUUID();
    NoteReadModel note1 =
        DataGenerator.createDefault(
            NoteReadModelGenerator.class,
            b -> b.applicationId(applicationId).notes("first note").createdBy("user-a"));
    NoteReadModel note2 =
        DataGenerator.createDefault(
            NoteReadModelGenerator.class,
            b -> b.applicationId(applicationId).notes("second note").createdBy("user-b"));

    when(applicationGateway.applicationExists(applicationId)).thenReturn(true);
    when(noteGateway.findByApplicationIdOrderByCreatedAtAsc(applicationId))
        .thenReturn(List.of(note1, note2));

    List<NoteReadModel> result = useCase.execute(applicationId);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).notes()).isEqualTo("first note");
    assertThat(result.get(0).createdBy()).isEqualTo("user-a");
    assertThat(result.get(1).notes()).isEqualTo("second note");
    assertThat(result.get(1).createdBy()).isEqualTo("user-b");
    verify(noteGateway, times(1)).findByApplicationIdOrderByCreatedAtAsc(applicationId);
  }

  @Test
  void givenApplicationExistsWithNoNotes_whenExecuted_thenReturnsEmptyList() {
    UUID applicationId = UUID.randomUUID();

    when(applicationGateway.applicationExists(applicationId)).thenReturn(true);
    when(noteGateway.findByApplicationIdOrderByCreatedAtAsc(applicationId)).thenReturn(List.of());

    List<NoteReadModel> result = useCase.execute(applicationId);

    assertThat(result).isEmpty();
    verify(noteGateway, times(1)).findByApplicationIdOrderByCreatedAtAsc(applicationId);
  }

  @Test
  void givenApplicationDoesNotExist_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();

    when(applicationGateway.applicationExists(applicationId)).thenReturn(false);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(applicationId))
        .withMessageContaining("No application found with id: " + applicationId);

    verify(noteGateway, never()).findByApplicationIdOrderByCreatedAtAsc(applicationId);
  }
}
