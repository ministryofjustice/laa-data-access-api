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
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static uk.gov.justice.laa.dstew.access.service.application.sharedAsserts.Application.verifyThatApplicationEntitySaved;
import static uk.gov.justice.laa.dstew.access.service.application.sharedAsserts.DomainEvent.verifyThatDomainEventSaved;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AssignCaseworkerTest extends BaseServiceTest {

    @Autowired
    private ApplicationService serviceUnderTest;

    @ParameterizedTest
    @MethodSource("validAssignEventDescriptionCases")
    void givenCaseworkerAndApplications_whenAssignCaseworker_thenAssignAndSave(
            String eventDescription
    ) throws JsonProcessingException {
        // given
        UUID applicationId = UUID.randomUUID();

        CaseworkerEntity expectedCaseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder -> builder.id(UUID.randomUUID()));

        ApplicationEntity existingApplicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
                builder.id(applicationId).caseworker(null)
        );

        ApplicationEntity expectedApplicationEntity = existingApplicationEntity.toBuilder().caseworker(expectedCaseworker).build();

        EventHistory eventHistory = EventHistory.builder()
                .eventDescription(eventDescription)
                .build();

        DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
                .applicationId(applicationId)
                .caseworkerId(expectedCaseworker.getId())
                .createdBy("")
                .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data(objectMapper.writeValueAsString(AssignApplicationDomainEventDetails.builder()
                        .applicationId(existingApplicationEntity.getId())
                        .caseWorkerId(expectedCaseworker.getId())
                        .eventDescription(eventHistory.getEventDescription())
                        .createdBy("")
                        .build()))
                .build();

        List<UUID> applicationIds = List.of(applicationId);

        when(applicationRepository.findAllById(eq(applicationIds))).thenReturn(List.of(existingApplicationEntity));
        when(caseworkerRepository.findById(expectedCaseworker.getId()))
                .thenReturn(Optional.of(expectedCaseworker));

        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        serviceUnderTest.assignCaseworker(expectedCaseworker.getId(), List.of(applicationId), eventHistory);

        // then
        verify(applicationRepository, times(1)).findAllById(eq(applicationIds));
        verify(caseworkerRepository, times(1)).findById(expectedCaseworker.getId());

        verifyThatApplicationEntitySaved(applicationRepository, expectedApplicationEntity, 1);
        verifyThatDomainEventSaved(domainEventRepository, objectMapper, expectedDomainEvent, 1);
    }

    @Test
    void givenNullCasworkerId_whenAsssignCaseworker_thenThrowNullPointerException() {
        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        Throwable thrown = catchThrowable(() -> serviceUnderTest.assignCaseworker(null, null, null));
        assertThat(thrown)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("caseworkerId is marked non-null but is null");

        // then
        verify(applicationRepository, never()).findAllById(any(Iterable.class));
        verify(caseworkerRepository, never()).findById(any(UUID.class));
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    @Test
    void givenNullApplicationIdInList_whenAsssignCaseworker_thenThrowValidationException() {
        setSecurityContext(TestConstants.Roles.WRITER);
        List<UUID> applicationIdsWithNull = Arrays.asList(UUID.randomUUID(), null, UUID.randomUUID());
        CaseworkerEntity expectedCaseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder -> builder.id(UUID.randomUUID()));
        when(caseworkerRepository.findById(expectedCaseworker.getId()))
                .thenReturn(Optional.of(expectedCaseworker));

        ValidationException expectedValidationException = new ValidationException(
                List.of("Request contains null values for ids")
        );

        // when
        Throwable thrown = catchThrowable(() -> serviceUnderTest.assignCaseworker(expectedCaseworker.getId(), applicationIdsWithNull, new EventHistory()));
        assertThat(thrown)
                .isInstanceOf(ValidationException.class)
                .usingRecursiveComparison()
                .isEqualTo(expectedValidationException);

        // then
        verify(applicationRepository, never()).findAllById(any(Iterable.class));
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    @Test
    void givenNonexistentCaseworker_whenAssignCaseworker_thenThrowResourceNotFoundException() {
        UUID nonexistentCaseworkerId = UUID.randomUUID();

        when(caseworkerRepository.findById(nonexistentCaseworkerId))
                .thenReturn(Optional.empty());

        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        Throwable thrown = catchThrowable(() -> serviceUnderTest.assignCaseworker(nonexistentCaseworkerId, List.of(UUID.randomUUID()), new EventHistory()));
        assertThat(thrown)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No caseworker found with id: " + nonexistentCaseworkerId);

        // then
        verify(applicationRepository, never()).findAllById(any(Iterable.class));
        verify(caseworkerRepository, times(1)).findById(nonexistentCaseworkerId);
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    @Test
    void givenEmptyApplicationIds_whenAssignCaseworker_thenNoActionTaken() {

        // given
        CaseworkerEntity expectedCaseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder -> builder.id(UUID.randomUUID()));
        when(caseworkerRepository.findById(expectedCaseworker.getId()))
                .thenReturn(Optional.of(expectedCaseworker));


        setSecurityContext(TestConstants.Roles.WRITER);

        List<UUID> emptyApplicationIds = Collections.emptyList();

        // when
        serviceUnderTest.assignCaseworker(expectedCaseworker.getId(), emptyApplicationIds, new EventHistory());

        // then
        verify(applicationRepository, times(1)).findAllById(emptyApplicationIds);
        verify(caseworkerRepository, times(1)).findById(expectedCaseworker.getId());
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    @Test
    void givenDuplicateApplicationIds_whenAssignCaseworker_thenOnlyDistinctIdsUsed() throws JsonProcessingException {
        UUID existingApplicationId = UUID.randomUUID();
        ApplicationEntity existingApplicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
                builder.id(existingApplicationId).caseworker(null)
        );

        CaseworkerEntity expectedCaseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder -> builder.id(UUID.randomUUID()));

        List<UUID> applicationIds = List.of(existingApplicationId, existingApplicationId, existingApplicationId);
        List<UUID> distinctApplicationIds = Stream.of(existingApplicationId).toList();

        ApplicationEntity expectedApplicationEntity = existingApplicationEntity.toBuilder().caseworker(expectedCaseworker).build();

        EventHistory eventHistory = EventHistory.builder()
                .eventDescription("Caseworker assigned.")
                .build();

        DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
                .applicationId(expectedApplicationEntity.getId())
                .caseworkerId(expectedCaseworker.getId())
                .createdBy("")
                .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data(objectMapper.writeValueAsString(AssignApplicationDomainEventDetails.builder()
                        .applicationId(existingApplicationEntity.getId())
                        .caseWorkerId(expectedCaseworker.getId())
                        .eventDescription(eventHistory.getEventDescription())
                        .createdBy("")
                        .build()))
                .build();

        when(applicationRepository.findAllById(eq(distinctApplicationIds))).thenReturn(List.of(existingApplicationEntity));
        when(caseworkerRepository.findById(expectedCaseworker.getId()))
                .thenReturn(Optional.of(expectedCaseworker));

        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        serviceUnderTest.assignCaseworker(expectedCaseworker.getId(), applicationIds, eventHistory);

        // then
        verify(applicationRepository, times(1)).findAllById(eq(distinctApplicationIds));
        verify(caseworkerRepository, times(1)).findById(expectedCaseworker.getId());

        verifyThatApplicationEntitySaved(applicationRepository, expectedApplicationEntity, 1);
        verifyThatDomainEventSaved(domainEventRepository, objectMapper, expectedDomainEvent, 1);
    }

    @Test
    void givenApplicationIsAlreadyAssignedToCaseworker_whenAssignCaseworker_thenNotUpdateApplication_andCreateDomainEvent() throws JsonProcessingException {

        UUID existingApplicationId = UUID.randomUUID();
        CaseworkerEntity existingCaseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder -> builder.id(UUID.randomUUID()));
        ApplicationEntity existingApplicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
                builder.id(existingApplicationId).caseworker(existingCaseworker)
        );

        List<UUID> applicationIds = List.of(existingApplicationId);

        EventHistory eventHistory = EventHistory.builder()
                .eventDescription("Caseworker assigned.")
                .build();

        DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
                .applicationId(existingApplicationEntity.getId())
                .caseworkerId(existingCaseworker.getId())
                .createdBy("")
                .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data(objectMapper.writeValueAsString(AssignApplicationDomainEventDetails.builder()
                        .applicationId(existingApplicationEntity.getId())
                        .caseWorkerId(existingCaseworker.getId())
                        .eventDescription(eventHistory.getEventDescription())
                        .createdBy("")
                        .build()))
                .build();

        when(applicationRepository.findAllById(eq(applicationIds))).thenReturn(List.of(existingApplicationEntity));
        when(caseworkerRepository.findById(existingCaseworker.getId()))
                .thenReturn(Optional.of(existingCaseworker));

        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        serviceUnderTest.assignCaseworker(existingCaseworker.getId(), applicationIds, eventHistory);

        // then
        verify(applicationRepository, times(1)).findAllById(eq(applicationIds));
        verify(caseworkerRepository, times(1)).findById(existingCaseworker.getId());
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verifyThatDomainEventSaved(domainEventRepository, objectMapper, expectedDomainEvent, 1);
    }

    @Test
    void givenMissingApplications_whenAssignCaseworker_thenThrowResourceNotFoundException() {
        UUID existingApplicationId = UUID.randomUUID();
        ApplicationEntity existingApplicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
                builder.id(existingApplicationId).caseworker(null)
        );

        CaseworkerEntity expectedCaseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder -> builder.id(UUID.randomUUID()));

        List<UUID> applicationIds = List.of(UUID.randomUUID(), existingApplicationId, UUID.randomUUID());

        EventHistory eventHistory = EventHistory.builder()
                .eventDescription("Case assigned.")
                .build();

        when(applicationRepository.findAllById(eq(applicationIds))).thenReturn(List.of(existingApplicationEntity));
        when(caseworkerRepository.findById(expectedCaseworker.getId()))
                .thenReturn(Optional.of(expectedCaseworker));

        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        Throwable thrown = catchThrowable(() -> serviceUnderTest.assignCaseworker(expectedCaseworker.getId(), applicationIds, eventHistory));
        assertThat(thrown)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No application found with ids: " + applicationIds.stream()
                        .filter(id -> !id.equals(existingApplicationId))
                        .map(UUID::toString)
                        .collect(Collectors.joining(",")));

        // then
        verify(applicationRepository, times(1)).findAllById(eq(applicationIds));
        verify(caseworkerRepository, times(1)).findById(expectedCaseworker.getId());
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    @Test
    void givenRoleReader_whenAssignCaseworker_thenThrowUnauthorizedException() {
        // given
        setSecurityContext(TestConstants.Roles.READER);

        // when
        Throwable thrown = catchThrowable(() -> serviceUnderTest.assignCaseworker(UUID.randomUUID(), List.of(UUID.randomUUID()), new EventHistory()));
        assertThat(thrown)
                .isInstanceOf(AuthorizationDeniedException.class)
                .hasMessage("Access Denied");

        // then
        verify(applicationRepository, never()).findAllById(any(Iterable.class));
        verify(caseworkerRepository, never()).findById(any(UUID.class));
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    @Test
    void givenNoRole_whenAssignCaseworker_thenThrowUnauthorizedException() {
        // given
        // no security context set

        // when
        Throwable thrown = catchThrowable(() -> serviceUnderTest.assignCaseworker(UUID.randomUUID(), List.of(UUID.randomUUID()), new EventHistory()));
        assertThat(thrown)
                .isInstanceOf(AuthorizationDeniedException.class)
                .hasMessage("Access Denied");

        // then
        verify(applicationRepository, never()).findAllById(any(Iterable.class));
        verify(caseworkerRepository, never()).findById(any(UUID.class));
        verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        verify(domainEventRepository, never()).save(any(DomainEventEntity.class));
    }

    private Stream<Arguments> validAssignEventDescriptionCases() {
        return Stream.of(
                Arguments.of("Assigned by system"),
                Arguments.of(""),
                Arguments.of((Object)null)
        );
    }
}
