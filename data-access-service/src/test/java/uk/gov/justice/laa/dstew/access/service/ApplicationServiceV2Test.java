package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.caseworker.CaseworkerFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationEntityFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationUpdateFactory;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationEqual;

public class ApplicationServiceV2Test extends BaseServiceTest {

    @Autowired
    private ApplicationService serviceUnderTest;

    @Nested
    class GetApplication {

        @Test
        public void givenApplicationEntityAndRoleReader_whenGetApplication_thenReturnMappedApplication() {
            // given
            ApplicationEntity expectedApplication = ApplicationEntityFactory.create();

            when(applicationRepository.findById(expectedApplication.getId())).thenReturn(Optional.of(expectedApplication));

            setSecurityContext(TestConstants.Roles.READER);

            // when
            Application actualApplication = serviceUnderTest.getApplication(expectedApplication.getId());

            // then
            assertApplicationEqual(expectedApplication, actualApplication);
            verify(applicationRepository, times(1)).findById(expectedApplication.getId());
        }

        @Test
        public void givenNoApplicationAndRoleReader_whenGetApplication_thenThrowApplicationNotFoundException() {

            // given
            UUID applicationId = UUID.randomUUID();
            when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

            setSecurityContext(TestConstants.Roles.READER);

            // when
            // then
            assertThatExceptionOfType(ApplicationNotFoundException.class)
                    .isThrownBy(() -> serviceUnderTest.getApplication(applicationId))
                    .withMessageContaining("No application found with id: " + applicationId);
            verify(applicationRepository, times(1)).findById(applicationId);
        }

        @Test
        public void givenApplicationAndNotRoleReader_whenGetApplication_thenThrowUnauthorizedException() {

            // given
            UUID applicationId = UUID.randomUUID();

            setSecurityContext(TestConstants.Roles.NO_ROLE);

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.getApplication(applicationId))
            .withMessageContaining("Access Denied");

            verify(applicationRepository, times(0)).findById(applicationId);
        }

        @Test
        public void givenApplicationAndNoRole_whenGetApplication_thenThrowUnauthorizedException() {

            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.getApplication(UUID.randomUUID()))
                    .withMessageContaining("Access Denied");

            verify(applicationRepository, times(0)).findById(any(UUID.class));
        }
    }

    @Nested
    class CreateApplication {

        @Test
        public void givenNewApplication_whenCreateApplication_thenReturnNewId() {

            // given
            UUID expectedId = UUID.randomUUID();
            ApplicationEntity withExpectedId = ApplicationEntityFactory.create(builder ->
                    builder.id(expectedId)
            );
            when(applicationRepository.save(any())).thenReturn(withExpectedId);

            setSecurityContext(TestConstants.Roles.WRITER);

            // when
            UUID actualId = serviceUnderTest.createApplication(APPLICATION_TO_CREATE);

            // then
            assertEquals(expectedId, actualId);

            verify(applicationRepository, times(1)).save(any());
        }

        @Test
        public void givenNewApplicationAndNotRoleReader_whenCreateApplication_thenThrowUnauthorizedException() {
            // given
            setSecurityContext(TestConstants.Roles.NO_ROLE);

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.createApplication(APPLICATION_TO_CREATE))
                    .withMessageContaining("Access Denied");

            verify(applicationRepository, times(0)).findById(any(UUID.class));
        }

        @Test
        public void givenNewApplicationAndNoRole_whenCreateApplication_thenThrowUnauthorizedException() {

            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.createApplication(APPLICATION_TO_CREATE))
                    .withMessageContaining("Access Denied");

            verify(applicationRepository, times(0)).findById(any(UUID.class));
        }

        @ParameterizedTest
        @MethodSource("invalidApplicationRequests")
        public void GivenInvalidApplicationAndRoleWriter_whenCreateApplication_thenValidationExceptionWithCorrectMessage(
                ApplicationCreateRequest applicationCreateRequest,
                ValidationException validationException
        ) {
            setSecurityContext(TestConstants.Roles.WRITER);

            Throwable thrown = catchThrowable(() -> serviceUnderTest.createApplication(applicationCreateRequest));
            assertThat(thrown)
                    .isInstanceOf(ValidationException.class)
                    .usingRecursiveComparison()
                    .isEqualTo(validationException);

            verify(applicationRepository, times(0)).findById(any(UUID.class));
        }

        private static final String APPLICATION_TO_CREATE_REFERENCE = "REF7327";
        private static final ApplicationCreateRequest APPLICATION_TO_CREATE = ApplicationCreateRequest.builder()
                .status(ApplicationStatus.IN_PROGRESS)
                .laaReference(APPLICATION_TO_CREATE_REFERENCE)
                .applicationContent(new HashMap<>() {{
                    put("test", "content");
                }})
                .build();

        public static final Stream<Arguments> invalidApplicationRequests() {
            return Stream.of(
                    Arguments.of(ApplicationCreateRequest.builder()
                                    .status(ApplicationStatus.IN_PROGRESS)
                                    .laaReference(APPLICATION_TO_CREATE_REFERENCE)
                                    .applicationContent(null)
                                    .build(),
                            new ValidationException(List.of(
                                    "ApplicationCreateRequest and its content cannot be null"
                            ))
                    ),
                    Arguments.of(ApplicationCreateRequest.builder()
                                    .laaReference(APPLICATION_TO_CREATE_REFERENCE)
                                    .applicationContent(new HashMap<>() {{
                                        put("test", "content");
                                    }})
                                    .build(),
                            new ValidationException(List.of(
                                    "Application status cannot be null"
                            ))
                    ),
                    Arguments.of(ApplicationCreateRequest.builder()
                                    .status(ApplicationStatus.IN_PROGRESS)
                                    .applicationContent(new HashMap<>() {{
                                        put("test", "content");
                                    }})
                                    .build(),
                            new ValidationException(List.of(
                                    "Application reference cannot be blank"
                            ))
                    ),
                    Arguments.of(ApplicationCreateRequest.builder()
                                    .status(ApplicationStatus.IN_PROGRESS)
                                    .laaReference(APPLICATION_TO_CREATE_REFERENCE)
                                    .applicationContent(new HashMap<>())
                                    .build(),
                            new ValidationException(List.of(
                                    "Application content cannot be empty"
                            ))
                    )
            );
        }
    }

    @Nested
    class UpdateApplication {
        @Test
        void givenNoApplication_whenUpdateApplication_thenThrowApplicationNotFoundException() {
            // given
            UUID applicationId = UUID.randomUUID();
            when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

            setSecurityContext(TestConstants.Roles.WRITER);

            // when / then
            assertThatExceptionOfType(ApplicationNotFoundException.class)
                    .isThrownBy(() -> serviceUnderTest.updateApplication(applicationId, new ApplicationUpdateRequest()))
                    .withMessageContaining("No application found with id: " + applicationId);
            verify(applicationRepository, times(1)).findById(applicationId);
        }

        @Test
        void givenApplication_whenUpdateApplication_thenUpdateAndSave() {
            // given
            UUID applicationId = UUID.randomUUID();
            ApplicationEntity expectedEntity = ApplicationEntityFactory.create(builder ->
                    builder.id(applicationId)
                            .applicationContent(new HashMap<>(Map.of("test", "change")))
            );
            ApplicationUpdateRequest updateRequest = ApplicationUpdateFactory.create();
            when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(expectedEntity));

            setSecurityContext(TestConstants.Roles.WRITER);

            // when
            serviceUnderTest.updateApplication(applicationId, updateRequest);

            // then
            verify(applicationRepository, times(1)).findById(applicationId);
            verify(applicationRepository, times(1)).save(expectedEntity);
            assertThat(expectedEntity.getModifiedAt()).isNotNull();
        }

        @ParameterizedTest
        @MethodSource("invalidApplicationUpdateRequests")
        void givenApplicationAndInvalidUpdateRequest_whenUpdateApplication_thenValidationExceptionWithCorrectMessage(
                UUID applicationId,
                ApplicationUpdateRequest applicationUpdateRequest,
                ValidationException validationException
        ) {
            // given
            ApplicationEntity expectedEntity = ApplicationEntityFactory.create(builder ->
                    builder.id(applicationId)
                            .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
            );
            when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(expectedEntity));

            setSecurityContext(TestConstants.Roles.WRITER);

            // when
            // then
            Throwable thrown = catchThrowable(() -> serviceUnderTest.updateApplication(applicationId, applicationUpdateRequest));
            assertThat(thrown)
                    .isInstanceOf(ValidationException.class)
                    .usingRecursiveComparison()
                    .isEqualTo(validationException);
            verify(applicationRepository, times(1)).findById(applicationId);
            verify(applicationRepository, never()).save(any());
        }

        @Test
        public void givenApplicationUpdateAndNotRoleWriter_whenCreateApplication_thenThrowUnauthorizedException() {
            // given
            UUID applicationId = UUID.randomUUID();

            setSecurityContext(TestConstants.Roles.READER);

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.updateApplication(applicationId, new ApplicationUpdateRequest()))
                    .withMessageContaining("Access Denied");
            verify(applicationRepository, never()).findById(applicationId);
            verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        }

        @Test
        public void givenApplicationUpdateAndNoRole_whenCreateApplication_thenThrowUnauthorizedException() {
            // given
            UUID applicationId = UUID.randomUUID();

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.updateApplication(applicationId, new ApplicationUpdateRequest()))
                    .withMessageContaining("Access Denied");
            verify(applicationRepository, never()).findById(applicationId);
            verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        }

        public static final Stream<Arguments> invalidApplicationUpdateRequests() {
            return Stream.of(
                    Arguments.of(UUID.randomUUID(),
                            ApplicationUpdateFactory.create(builder -> builder
                                    .status(ApplicationStatus.IN_PROGRESS)
                                    .applicationContent(null)),
                            new ValidationException(List.of(
                                    "ApplicationUpdateRequest and its content cannot be null"
                            ))
                    ),
                    Arguments.of(UUID.randomUUID(),
                            ApplicationUpdateFactory.create(builder -> builder
                                    .status(ApplicationStatus.IN_PROGRESS)
                                    .applicationContent(new HashMap<>())),
                            new ValidationException(List.of(
                                    "Application content cannot be empty"
                            ))
                    )
            );
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class AssignCaseworker {

        @ParameterizedTest
        @MethodSource("validAssignEventDescriptionCases")
        void givenCaseworkerAndApplications_whenAssignCaseworker_thenAssignAndSave(
                String eventDescription
        ) throws JsonProcessingException {
            // given
            UUID applicationId = UUID.randomUUID();

            CaseworkerEntity expectedCaseworker = CaseworkerFactory.create();

            ApplicationEntity existingApplicationEntity = ApplicationEntityFactory.create(builder ->
                    builder.id(applicationId).caseworker(null)
            );

            ApplicationEntity expectedApplicationEntity = existingApplicationEntity.toBuilder().caseworker(expectedCaseworker).build();

            EventHistory eventHistory = EventHistory.builder()
                    .eventDescription(eventDescription)
                    .build();

            DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
                    .applicationId(applicationId)
                    .caseWorkerId(expectedCaseworker.getId())
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

            verifyThatApplicationEntitySaved(expectedApplicationEntity, 1);
            verifyThatDomainEventSaved(expectedDomainEvent, 1);
        }

        @Test
        void givenNonexistentCaseworker_whenAssignCaseworker_thenThrowCaseworkerNotFoundException() {
        }

        @Test
        void givenDuplicateApplicationIds_whenAssignCaseworker_thenOnlyDistinctIdsUsed() {

        }

        @Test
        void givenMissingApplications_whenAssignCaseworker_thenThrowApplicationNotFoundException() {
            UUID existingApplicationId = UUID.randomUUID();
            ApplicationEntity existingApplicationEntity = ApplicationEntityFactory.create(builder ->
                    builder.id(existingApplicationId).caseworker(null)
            );

            CaseworkerEntity expectedCaseworker = CaseworkerFactory.create();

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
                    .isInstanceOf(ApplicationNotFoundException.class)
                    .hasMessage("No application found with ids: " + applicationIds.stream()
                            .filter(id -> !id.equals(existingApplicationId))
                            .map(UUID::toString)
                            .collect(Collectors.joining(",")));

            // then
            verify(applicationRepository, times(1)).findAllById(eq(applicationIds));
            verify(caseworkerRepository, times(1)).findById(expectedCaseworker.getId());
            verify(applicationRepository, never()).save(any(ApplicationEntity.class));
        }

        @Test
        void givenNullEventDescription_whenAssignCaseworker_thenAssignAndSave() {

        }

        private Stream<Arguments> validAssignEventDescriptionCases() {
            return Stream.of(
                    Arguments.of("Unassigned by system"),
                    Arguments.of(""),
                    Arguments.of((Object)null)
            );
        }

        private void verifyThatApplicationEntitySaved(ApplicationEntity expectedApplicationEntity, int timesCalled) {
            ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
            verify(applicationRepository, times(timesCalled)).save(captor.capture());
            ApplicationEntity actualApplicationEntity = captor.getValue();
            assertThat(expectedApplicationEntity)
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringFields("createdAt", "modifiedAt")
                    .isEqualTo(actualApplicationEntity);
            assertThat(actualApplicationEntity.getCreatedAt()).isNotNull();
            assertThat(actualApplicationEntity.getModifiedAt()).isNotNull();
        }

        private void verifyThatDomainEventSaved(DomainEventEntity expectedDomainEvent, int timesCalled) throws JsonProcessingException {
            ArgumentCaptor<DomainEventEntity> captor = ArgumentCaptor.forClass(DomainEventEntity.class);
            verify(domainEventRepository, times(timesCalled)).save(captor.capture());
            DomainEventEntity actualDomainEvent = captor.getValue();
            assertThat(expectedDomainEvent)
                    .usingRecursiveComparison()
                    .ignoringFields("createdAt", "data")
                    .isEqualTo(actualDomainEvent);
            assertThat(actualDomainEvent.getCreatedAt()).isNotNull();

            Map<String, Object> expectedData = objectMapper.readValue(expectedDomainEvent.getData(), Map.class);
            Map<String, Object> actualData = objectMapper.readValue(actualDomainEvent.getData(), Map.class);
            assertThat(expectedData)
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringFields("createdAt")
                    .isEqualTo(actualData);
            assertThat(actualData.get("createdAt")).isNotNull();
        }
    }

    @Nested
    class UnassignCaseworker {
        @Test
        void givenAssignedCaseworker_whenUnassignCaseworker_thenUnassignAndSave() {

        }

        @Test
        void givenAlreadyUnassigned_whenUnassignCaseworker_thenNotSave() {

        }

        @Test
        void givenNonexistentApplication_whenUnassignCaseworker_thenThrowApplicationNotFoundException() {

        }
    }

    @Nested
    class ReassignCaseworker {

    }
}