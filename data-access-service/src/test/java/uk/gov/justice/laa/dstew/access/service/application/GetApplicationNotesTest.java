package uk.gov.justice.laa.dstew.access.service.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationNotesResponse;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.notes.NoteEntityGenerator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GetApplicationNotesTest extends BaseServiceTest {

    @Autowired
    private ApplicationService serviceUnderTest;

    @Test
    void givenApplicationExistsWithNotes_whenGetApplicationNotes_thenReturnNotesInAscendingOrder() {
        // given
        UUID applicationId = UUID.randomUUID();
        final ApplicationEntity application = DataGenerator.createDefault(ApplicationEntityGenerator.class,
                builder -> builder.id(applicationId));

        Instant earlier = Instant.parse("2024-01-01T10:00:00Z");
        Instant later   = Instant.parse("2024-01-01T11:00:00Z");

        NoteEntity note1 = DataGenerator.createDefault(NoteEntityGenerator.class,
                builder -> builder.applicationId(applicationId).notes("first note").createdBy("user-a").createdAt(earlier));

        NoteEntity note2 = DataGenerator.createDefault(NoteEntityGenerator.class,
                builder -> builder.applicationId(applicationId).notes("second note").createdBy("user-b").createdAt(later));

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(noteRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId))
                .thenReturn(List.of(note1, note2));

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        // when
        ApplicationNotesResponse response = serviceUnderTest.getApplicationNotes(applicationId);

        // then
        assertThat(response.getNotes()).isNotNull();
        assertThat(response.getNotes().size()).isEqualTo(2);
        assertThat(response.getNotes().get(0).getNote()).isEqualTo("first note");
        assertThat(response.getNotes().get(0).getCreatedBy()).isEqualTo("user-a");
        assertThat(response.getNotes().get(1).getNote()).isEqualTo("second note");
        assertThat(response.getNotes().get(1).getCreatedBy()).isEqualTo("user-b");

        verify(noteRepository).findByApplicationIdOrderByCreatedAtAsc(applicationId);
    }

    @Test
    void givenApplicationExistsWithNoNotes_whenGetApplicationNotes_thenReturnEmptyList() {
        // given
        UUID applicationId = UUID.randomUUID();
        final ApplicationEntity application = DataGenerator.createDefault(ApplicationEntityGenerator.class,
                builder -> builder.id(applicationId));

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(noteRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId)).thenReturn(List.of());

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        // when
        ApplicationNotesResponse response = serviceUnderTest.getApplicationNotes(applicationId);

        // then
        assertThat(response.getNotes()).isNotNull();
        assertThat(response.getNotes().isEmpty()).isTrue();
    }

    @Test
    void givenApplicationDoesNotExist_whenGetApplicationNotes_thenThrowResourceNotFoundException() {
        // given
        UUID applicationId = UUID.randomUUID();

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        // when / then
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> serviceUnderTest.getApplicationNotes(applicationId))
                .withMessageContaining("No application found with id: " + applicationId);

        verify(noteRepository, never()).findByApplicationIdOrderByCreatedAtAsc(applicationId);
    }

    @Test
    void givenNotCaseworkerRole_whenGetApplicationNotes_thenThrowAuthorizationDeniedException() {
        // given
        setSecurityContext(TestConstants.Roles.NO_ROLE);

        // when / then
        assertThatExceptionOfType(AuthorizationDeniedException.class)
                .isThrownBy(() -> serviceUnderTest.getApplicationNotes(UUID.randomUUID()))
                .withMessageContaining("Access Denied");

        verify(noteRepository, never()).findByApplicationIdOrderByCreatedAtAsc(null);
    }

    @Test
    void givenNoRole_whenGetApplicationNotes_thenThrowAuthorizationDeniedException() {
        // given
        // no security context set

        // when / then
        assertThatExceptionOfType(AuthorizationDeniedException.class)
                .isThrownBy(() -> serviceUnderTest.getApplicationNotes(UUID.randomUUID()))
                .withMessageContaining("Access Denied");

        verify(noteRepository, never()).findByApplicationIdOrderByCreatedAtAsc(null);
    }
}
