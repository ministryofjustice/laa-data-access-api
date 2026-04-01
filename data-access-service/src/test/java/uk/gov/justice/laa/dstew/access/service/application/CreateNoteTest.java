package uk.gov.justice.laa.dstew.access.service.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.notes.CreateNoteRequestGenerator;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CreateNoteTest extends BaseServiceTest {
    @Autowired
    private ApplicationService serviceUnderTest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void givenExistingApplication_whenCreateNote_thenCreateOkAndDomainEventPersisted() throws Exception {
        // given
        UUID applicationId = UUID.randomUUID();
        UUID caseworkerId = UUID.randomUUID();
        String applicationNote = "this is a test of notes";
        ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
        ArgumentCaptor<DomainEventEntity> eventCaptor = ArgumentCaptor.forClass(DomainEventEntity.class);

        final ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class,
                builder -> builder.id(applicationId)
                        .caseworker(CaseworkerEntity.builder().id(caseworkerId).build()));

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(entity));

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        CreateNoteRequest request = DataGenerator.createDefault(CreateNoteRequestGenerator.class,
                builder -> builder.notes(applicationNote));

        // when
        serviceUnderTest.createApplicationNote(applicationId, request);

        // then - verify note saved
        verify(noteRepository, times(1)).save(noteCaptor.capture());
        NoteEntity actualNoteEntity = noteCaptor.getValue();
        assertEquals(applicationId, actualNoteEntity.getApplicationId());
        assertEquals(applicationNote, actualNoteEntity.getNotes());

        // then - verify domain event saved
        verify(domainEventRepository, times(1)).save(eventCaptor.capture());
        DomainEventEntity actualEvent = eventCaptor.getValue();
        assertEquals(DomainEventType.APPLICATION_NOTES, actualEvent.getType());
        assertEquals(applicationId, actualEvent.getApplicationId());
        assertEquals(caseworkerId, actualEvent.getCaseworkerId());
        assertNotNull(actualEvent.getData());
        assertNotNull(actualEvent.getCreatedAt());

        // Verify data JSON contains required fields
        JsonNode eventData = objectMapper.readTree(actualEvent.getData());
        assertEquals(applicationId.toString(), eventData.get("applicationId").asText());
        assertEquals(caseworkerId.toString(), eventData.get("caseworkerId").asText());
        assertNotNull(eventData.get("request"));
        assertNotNull(eventData.get("createdDate"));
        assertTrue(eventData.get("request").asText().contains(applicationNote));
    }

    @Test
    void givenApplicationDoesNotExist_whenCreateNote_thenThrowResourceNotFoundException() {
        // given
        UUID applicationId = UUID.randomUUID();
        String applicationNote = "this is a test of notes";

        CreateNoteRequest request = DataGenerator.createDefault(CreateNoteRequestGenerator.class,
                builder -> builder.notes(applicationNote));
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        // when / then
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> serviceUnderTest.createApplicationNote(applicationId, request))
                .withMessageContaining("No application found with id: " + applicationId);

        verify(noteRepository, never()).save(any(NoteEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

}
