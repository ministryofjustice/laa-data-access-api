package uk.gov.justice.laa.dstew.access.service.application;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CreateNoteTest extends BaseServiceTest {
  @Autowired private ApplicationService serviceUnderTest;

  @Test
  void givenExistingApplication_whenCreateNote_thenCreateOk() {
    UUID applicationId = UUID.randomUUID();
    String applicationNote = "this is a test of notes";
    ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);

    final ApplicationEntity entity =
        DataGenerator.createDefault(
            ApplicationEntityGenerator.class, builder -> builder.id(applicationId));

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(entity));

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    serviceUnderTest.createApplicationNote(applicationId, applicationNote);

    verify(noteRepository, times(1)).save(noteCaptor.capture());
    NoteEntity actualNoteEntity = noteCaptor.getValue();
    assertEquals(applicationId, actualNoteEntity.getApplicationId());
    assertEquals(applicationNote, actualNoteEntity.getNotes());
  }

  @Test
  void givenApplicationDoesNotExist_whenCreateNote_thenThrowResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    String applicationNote = "this is a test of notes";

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

    setSecurityContext(TestConstants.Roles.CASEWORKER);
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> serviceUnderTest.createApplicationNote(applicationId, applicationNote))
        .withMessageContaining("No application found with id: " + applicationId);

    verify(noteRepository, never()).save(any(NoteEntity.class));
  }
}
