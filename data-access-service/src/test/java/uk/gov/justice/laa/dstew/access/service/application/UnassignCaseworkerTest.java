package uk.gov.justice.laa.dstew.access.service.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.service.application.sharedAsserts.Application.verifyThatApplicationEntitySaved;
import static uk.gov.justice.laa.dstew.access.service.application.sharedAsserts.DomainEvent.verifyThatDomainEventSaved;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UnassignCaseworkerTest extends BaseServiceTest {

    @Autowired
    private ApplicationService serviceUnderTest;

    @ParameterizedTest
    @MethodSource("validUnassignEventDescriptionCases")
    void givenAssignedCaseworker_whenUnassignCaseworker_thenUnassignAndSave(
            String eventDescription
    ) throws JsonProcessingException {
        // given
        UUID applicationId = UUID.randomUUID();

        CaseworkerEntity expectedCaseworker = caseworkerFactory.createDefault();

        ApplicationEntity existingApplicationEntity = applicationEntityFactory.createDefault(builder ->
                builder.id(applicationId).caseworker(expectedCaseworker)
        );

        ApplicationEntity expectedApplicationEntity = existingApplicationEntity.toBuilder().caseworker(null).build();

        EventHistory eventHistory = EventHistory.builder()
                .eventDescription(eventDescription)
                .build();

        DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
                .applicationId(applicationId)
                .caseworkerId(null)
                .createdBy("")
                .type(DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER)
                .data(objectMapper.writeValueAsString(AssignApplicationDomainEventDetails.builder()
                        .applicationId(existingApplicationEntity.getId())
                        .caseWorkerId(null)
                        .eventDescription(eventHistory.getEventDescription())
                        .createdBy("")
                        .build()))
                .build();

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(existingApplicationEntity));

        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        serviceUnderTest.unassignCaseworker(applicationId, eventHistory);

        // then
        verify(applicationRepository, times(1)).findById(applicationId);

        verifyThatApplicationEntitySaved(applicationRepository, expectedApplicationEntity, 1);
        verifyThatDomainEventSaved(domainEventRepository, objectMapper, expectedDomainEvent, 1);
    }

    @Test
    void givenAlreadyUnassigned_whenUnassignCaseworker_thenNotSave() throws JsonProcessingException {
        UUID applicationId = UUID.randomUUID();
        ApplicationEntity existingApplicationEntity = applicationEntityFactory.createDefault(builder ->
                builder.id(applicationId).caseworker(null)
        );

        EventHistory eventHistory = EventHistory.builder()
                .eventDescription("Unassigned")
                .build();

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(existingApplicationEntity));

        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        serviceUnderTest.unassignCaseworker(applicationId, eventHistory);

        // then
        verify(applicationRepository, times(1)).findById(applicationId);
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    @ParameterizedTest
    @MethodSource("invalidUnassignApplicationIdCases")
    void givenNonexistentApplication_whenUnassignCaseworker_thenThrowResourceNotFoundException(
            UUID applicationId
    ) {

        // given
        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        Throwable thrown = catchThrowable(() -> serviceUnderTest.unassignCaseworker(applicationId, new EventHistory()));
        assertThat(thrown)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No application found with id: " + applicationId);

        // then
        verify(applicationRepository, times(1)).findById(applicationId);
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    @Test
    void givenRoleReader_whenUnassignCaseworker_thenThrowUnauthorizedException() {
        // given
        setSecurityContext(TestConstants.Roles.READER);

        // when
        Throwable thrown = catchThrowable(() -> serviceUnderTest.unassignCaseworker(UUID.randomUUID(), new EventHistory()));
        assertThat(thrown)
                .isInstanceOf(AuthorizationDeniedException.class)
                .hasMessage("Access Denied");

        // then
        verify(applicationRepository, never()).findAllById(any(Iterable.class));
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    @Test
    void givenNoRole_whenUnassignCaseworker_thenThrowUnauthorizedException() {
        // given
        // no security context set

        // when
        Throwable thrown = catchThrowable(() -> serviceUnderTest.unassignCaseworker(UUID.randomUUID(), new EventHistory()));
        assertThat(thrown)
                .isInstanceOf(AuthorizationDeniedException.class)
                .hasMessage("Access Denied");

        // then
        verify(applicationRepository, never()).findAllById(any(Iterable.class));
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    private Stream<Arguments> validUnassignEventDescriptionCases() {
        return Stream.of(
                Arguments.of("Assigned by system"),
                Arguments.of(""),
                Arguments.of((Object)null)
        );
    }

    private Stream<Arguments> invalidUnassignApplicationIdCases() {
        return Stream.of(
                Arguments.of(UUID.randomUUID()),
                Arguments.of((UUID)null)
        );
    }
}
