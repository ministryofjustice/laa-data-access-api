package uk.gov.justice.laa.dstew.access.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.notes.CreateNoteService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.notes.CreateNoteRequestGenerator;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CreateNoteTest extends BaseServiceTest {
  @Autowired private CreateNoteService serviceUnderTest;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void givenExistingApplication_whenCreateNote_thenNoteSavedAndDomainEventPublished()
      throws JacksonException {
    // given
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    CaseworkerEntity caseworker =
        DataGenerator.createDefault(CaseworkerGenerator.class, builder -> builder.id(caseworkerId));

    ApplicationEntity entity =
        DataGenerator.createDefault(
            ApplicationEntityGenerator.class,
            builder -> builder.id(applicationId).caseworker(caseworker));

    CreateNoteRequest request =
        DataGenerator.createDefault(
            CreateNoteRequestGenerator.class, builder -> builder.notes("this is a test of notes"));

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(entity));
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    serviceUnderTest.createApplicationNote(applicationId, request);

    // then — note saved
    ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
    verify(noteRepository).save(noteCaptor.capture());
    NoteEntity savedNote = noteCaptor.getValue();
    assertThat(savedNote.getApplicationId()).isEqualTo(applicationId);
    assertThat(savedNote.getNotes()).isEqualTo(request.getNotes());

    // then — domain event saved
    ArgumentCaptor<DomainEventEntity> eventCaptor =
        ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository, times(1)).save(eventCaptor.capture());
    DomainEventEntity actualEvent = eventCaptor.getValue();

    assertThat(actualEvent.getApplicationId()).isEqualTo(applicationId);
    assertThat(actualEvent.getCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(actualEvent.getType()).isEqualTo(DomainEventType.APPLICATION_NOTES);
    assertThat(actualEvent.getCreatedBy()).isEqualTo("");
    assertThat(actualEvent.getCreatedAt()).isNotNull();
    assertThat(actualEvent.getServiceName()).isEqualTo(ServiceName.CIVIL_APPLY);

    // then — domain event data JSON contains required fields
    Map<String, Object> actualData = objectMapper.readValue(actualEvent.getData(), Map.class);
    assertThat(actualData.get("applicationId")).isEqualTo(applicationId.toString());
    assertThat(actualData.get("caseworkerId")).isEqualTo(caseworkerId.toString());
    assertThat(actualData.get("createdDate")).isNotNull();
    assertThat((String) actualData.get("request")).contains("this is a test of notes");
  }

  @Test
  void givenApplicationDoesNotExist_whenCreateNote_thenThrowResourceNotFoundException() {
    // given
    UUID applicationId = UUID.randomUUID();
    CreateNoteRequest request =
        DataGenerator.createDefault(
            CreateNoteRequestGenerator.class, builder -> builder.notes("this is a test of notes"));

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    Throwable thrown =
        catchThrowable(() -> serviceUnderTest.createApplicationNote(applicationId, request));

    // then
    assertThat(thrown)
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("No application found with id: " + applicationId);

    verify(noteRepository, never()).save(any(NoteEntity.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
  }

  @Test
  void givenNotCaseworkerRole_whenCreateNote_thenThrowAuthorizationDeniedException() {
    // given
    setSecurityContext(TestConstants.Roles.NO_ROLE);

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                serviceUnderTest.createApplicationNote(
                    UUID.randomUUID(),
                    DataGenerator.createDefault(CreateNoteRequestGenerator.class)));

    // then
    assertThat(thrown).isInstanceOf(AuthorizationDeniedException.class).hasMessage("Access Denied");

    verify(applicationRepository, never()).findById(any(UUID.class));
    verify(noteRepository, never()).save(any(NoteEntity.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
  }

  @Test
  void givenNoRole_whenCreateNote_thenThrowAuthorizationDeniedException() {
    // given
    // no security context set

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                serviceUnderTest.createApplicationNote(
                    UUID.randomUUID(),
                    DataGenerator.createDefault(CreateNoteRequestGenerator.class)));

    // then
    assertThat(thrown).isInstanceOf(AuthorizationDeniedException.class).hasMessage("Access Denied");

    verify(applicationRepository, never()).findById(any(UUID.class));
    verify(noteRepository, never()).save(any(NoteEntity.class));
    verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
  }
}
