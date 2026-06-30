package uk.gov.justice.laa.dstew.access.usecase.createnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.createnote.infrastructure.NoteGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.createnote.CreateNoteCommandGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationDomainGenerator;

@ExtendWith(MockitoExtension.class)
class CreateNoteUseCaseTest {

  @Mock private ApplicationGateway applicationGateway;
  @Mock private NoteGateway noteGateway;
  @Mock private DomainEventRepository domainEventRepository;
  @Mock private ServiceNameContext serviceNameContext;

  private CreateNoteUseCase useCase;

  @BeforeEach
  void setUp() {
    SaveDomainEventService saveDomainEventService =
        new SaveDomainEventService(domainEventRepository, new ObjectMapper(), serviceNameContext);
    useCase = new CreateNoteUseCase(applicationGateway, noteGateway, saveDomainEventService);
    Mockito.lenient().when(serviceNameContext.getServiceName()).thenReturn(ServiceName.CIVIL_APPLY);
  }

  @Test
  void givenApplicationWithCaseworker_whenExecuted_thenNoteSavedAndDomainEventPublished() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    ApplicationDomain applicationDomain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class, b -> b.id(applicationId).caseworkerId(caseworkerId));
    CreateNoteCommand command =
        DataGenerator.createDefault(
            CreateNoteCommandGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .noteText("test note")
                    .serialisedNoteRequest("{\"notes\":\"test note\"}"));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(applicationDomain));

    useCase.execute(command);

    verify(noteGateway).saveNote(applicationId, "test note");

    ArgumentCaptor<DomainEventEntity> eventCaptor =
        ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository).save(eventCaptor.capture());
    DomainEventEntity event = eventCaptor.getValue();
    assertThat(event.getApplicationId()).isEqualTo(applicationId);
    assertThat(event.getCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(event.getType()).isEqualTo(DomainEventType.APPLICATION_NOTES);
    assertThat(event.getData()).contains("test note");
  }

  @Test
  void
      givenApplicationWithNoCaseworker_whenExecuted_thenDomainEventPublishedWithNullCaseworkerId() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDomain applicationDomain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class, b -> b.id(applicationId).caseworkerId(null));
    CreateNoteCommand command =
        DataGenerator.createDefault(
            CreateNoteCommandGenerator.class, b -> b.applicationId(applicationId));

    when(applicationGateway.findByApplicationId(applicationId))
        .thenReturn(Optional.of(applicationDomain));

    useCase.execute(command);

    verify(noteGateway).saveNote(eq(applicationId), any());

    ArgumentCaptor<DomainEventEntity> captor = ArgumentCaptor.forClass(DomainEventEntity.class);
    verify(domainEventRepository).save(captor.capture());
    assertThat(captor.getValue().getCaseworkerId()).isNull();
  }

  @Test
  void givenApplicationNotFound_whenExecuted_thenThrowsResourceNotFoundExceptionAndNoSaves() {
    UUID applicationId = UUID.randomUUID();
    CreateNoteCommand command =
        DataGenerator.createDefault(
            CreateNoteCommandGenerator.class, b -> b.applicationId(applicationId));

    when(applicationGateway.findByApplicationId(applicationId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessageContaining("No application found with id: " + applicationId);

    verify(noteGateway, never()).saveNote(any(), any());
    verify(domainEventRepository, never()).save(any());
  }
}
