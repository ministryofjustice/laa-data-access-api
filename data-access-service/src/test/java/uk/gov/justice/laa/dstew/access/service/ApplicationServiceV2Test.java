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
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ApplicationServiceV2Test extends BaseServiceTest {

    @Autowired
    private ApplicationService serviceUnderTest;

    @Nested
    class GetApplication {

        @Test
        public void givenApplicationEntityAndRoleReader_whenGetApplication_thenReturnMappedApplication() {
            // given
            ApplicationEntity expectedApplication = applicationEntityFactory.createDefault();

            when(applicationRepository.findById(expectedApplication.getId())).thenReturn(Optional.of(expectedApplication));

            setSecurityContext(TestConstants.Roles.READER);

            // when
            Application actualApplication = serviceUnderTest.getApplication(expectedApplication.getId());

            // then
            assertApplicationEqual(expectedApplication, actualApplication);
            verify(applicationRepository, times(1)).findById(expectedApplication.getId());
        }

        @Test
        public void givenNoApplicationAndRoleReader_whenGetApplication_thenThrowResourceNotFoundException() {

            // given
            UUID applicationId = UUID.randomUUID();
            when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

            setSecurityContext(TestConstants.Roles.READER);

            // when
            // then
            assertThatExceptionOfType(ResourceNotFoundException.class)
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

        public void assertApplicationEqual(ApplicationEntity expectedApplication, Application actualApplication) {
            assertThat(actualApplication.getApplicationStatus()).isEqualTo(expectedApplication.getStatus());
            assertThat(actualApplication.getLaaReference()).isEqualTo(expectedApplication.getLaaReference());
            assertThat(actualApplication.getApplicationContent())
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .isEqualTo(expectedApplication.getApplicationContent());

            assertIndividualCollectionsEqual(actualApplication.getIndividuals(), expectedApplication.getIndividuals());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CreateApplication {

        @Test
        public void givenNewApplication_whenCreateApplication_thenReturnNewId() {

            // given
            UUID expectedId = UUID.randomUUID();
            ApplicationEntity withExpectedId = applicationEntityFactory.createDefault(builder ->
                    builder.id(expectedId)
            );

            ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.createDefault();

            when(applicationRepository.save(any())).thenReturn(withExpectedId);

            setSecurityContext(TestConstants.Roles.WRITER);

            // when
            UUID actualId = serviceUnderTest.createApplication(applicationCreateRequest);

            // then
            assertEquals(expectedId, actualId);

            verifyThatApplicationSaved(applicationCreateRequest, 1);
        }

        @Test
        public void givenNewApplicationAndNotRoleReader_whenCreateApplication_thenThrowUnauthorizedException() {
            // given
            setSecurityContext(TestConstants.Roles.NO_ROLE);

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.createApplication(applicationCreateRequestFactory.createDefault()))
                    .withMessageContaining("Access Denied");

            verify(applicationRepository, times(0)).findById(any(UUID.class));
        }

        @Test
        public void givenNewApplicationAndNoRole_whenCreateApplication_thenThrowUnauthorizedException() {

            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.createApplication(applicationCreateRequestFactory.createDefault()))
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

            verify(applicationRepository, never()).findById(any(UUID.class));
            verify(applicationRepository, never()).save(any());
        }

        private Stream<Arguments> invalidApplicationRequests() {
            return Stream.concat(
                    invalidApplicationSpecificCases(),
                    invalidIndividualSpecificCases()
            );
        }

        private Stream<Arguments> invalidApplicationSpecificCases() {
            return Stream.of(
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.applicationContent(null)),
                            new ValidationException(List.of(
                                    "ApplicationCreateRequest and its content cannot be null"
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.status(null)),
                            new ValidationException(List.of(
                                    "Application status cannot be null"
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.laaReference(null)),
                            new ValidationException(List.of(
                                    "Application reference cannot be blank"
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.laaReference("")),
                            new ValidationException(List.of(
                                    "Application reference cannot be blank"
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.applicationContent(new HashMap<>())),
                            new ValidationException(List.of(
                                    "Application content cannot be empty"
                            ))
                    )
            );
        }

        private Stream<Arguments> invalidIndividualSpecificCases() {
            return Stream.of(
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(Collections.singletonList(null))),
                            new ValidationException(List.of(
                                    "Individual cannot be null."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(individualFactory.createDefault(builderInd ->
                                            builderInd.firstName(null))))),
                            new ValidationException(List.of(
                                    "First name must be populated."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(individualFactory.createDefault(builderInd ->
                                            builderInd.firstName(""))))),
                            new ValidationException(List.of(
                                    "First name must be populated."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(individualFactory.createDefault(builderInd ->
                                            builderInd.lastName(null))))),
                            new ValidationException(List.of(
                                    "Last name must be populated."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(individualFactory.createDefault(builderInd ->
                                            builderInd.lastName(""))))),
                            new ValidationException(List.of(
                                    "Last name must be populated."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(individualFactory.createDefault(builderInd ->
                                            builderInd.details(null))))),
                            new ValidationException(List.of(
                                    "Individual details must be populated."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(individualFactory.createDefault(builderInd ->
                                            builderInd.details(new HashMap<>()))))),
                            new ValidationException(List.of(
                                    "Individual details must be populated."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(individualFactory.createDefault(builderInd ->
                                            builderInd.dateOfBirth(null))))),
                            new ValidationException(List.of(
                                    "Date of birth must be populated."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(individualFactory.createDefault(builderInd ->
                                            builderInd
                                                    .firstName(null)
                                                    .lastName(null)
                                                    .dateOfBirth(null)
                                                    .details(new HashMap<>()))))),
                            new ValidationException(List.of(
                                    "First name must be populated.",
                                    "Last name must be populated.",
                                    "Individual details must be populated.",
                                    "Date of birth must be populated."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(individualFactory.createDefault(builderInd ->
                                            builderInd
                                                    .firstName(null)
                                                    .lastName(null)
                                                    .dateOfBirth(null)
                                                    .details(null))))),
                            new ValidationException(List.of(
                                    "First name must be populated.",
                                    "Last name must be populated.",
                                    "Individual details must be populated.",
                                    "Date of birth must be populated."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(
                                            individualFactory.createRandom(builderInd ->
                                                builderInd.firstName(null)),
                                            individualFactory.createRandom(builderInd ->
                                                builderInd.lastName(null))
                                       )
                                    )),
                            new ValidationException(List.of(
                                    "First name must be populated.",
                                    "Last name must be populated."
                            ))
                    ),
                    Arguments.of(applicationCreateRequestFactory.createDefault(builder ->
                                    builder.individuals(List.of(
                                                    individualFactory.createRandom(builderInd ->
                                                            builderInd.firstName(null).lastName(null)),
                                                    individualFactory.createRandom(builderInd ->
                                                            builderInd.lastName(null).dateOfBirth(null))
                                            )
                                    )),
                            new ValidationException(List.of(
                                    "First name must be populated.",
                                    "Last name must be populated.",
                                    "Date of birth must be populated."
                            ))
                    )
            );
        }

        private void verifyThatApplicationSaved(ApplicationCreateRequest applicationCreateRequest, int timesCalled) {
            ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
            verify(applicationRepository, times(timesCalled)).save(captor.capture());
            ApplicationEntity actualApplicationEntity = captor.getValue();

            assertThat(actualApplicationEntity.getStatus()).isEqualTo(applicationCreateRequest.getStatus());
            assertThat(actualApplicationEntity.getLaaReference()).isEqualTo(applicationCreateRequest.getLaaReference());
            assertThat(actualApplicationEntity.getApplicationContent())
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .isEqualTo(applicationCreateRequest.getApplicationContent());

            assertIndividualCollectionsEqual(applicationCreateRequest.getIndividuals(), actualApplicationEntity.getIndividuals());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UpdateApplication {
        @Test
        void givenNoApplication_whenUpdateApplication_thenThrowResourceNotFoundException() {
            // given
            UUID applicationId = UUID.randomUUID();
            when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

            setSecurityContext(TestConstants.Roles.WRITER);

            // when / then
            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> serviceUnderTest.updateApplication(applicationId, new ApplicationUpdateRequest()))
                    .withMessageContaining("No application found with id: " + applicationId);
            verify(applicationRepository, times(1)).findById(applicationId);
        }

        @Test
        void givenApplication_whenUpdateApplication_thenUpdateAndSave() {
            // given
            UUID applicationId = UUID.randomUUID();
            ApplicationEntity expectedEntity = applicationEntityFactory.createDefault(builder ->
                    builder.id(applicationId)
                            .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
            );
            ApplicationUpdateRequest updateRequest = applicationUpdateRequestFactory.createDefault();
            when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(expectedEntity));

            setSecurityContext(TestConstants.Roles.WRITER);

            // when
            serviceUnderTest.updateApplication(applicationId, updateRequest);

            // then
            verify(applicationRepository, times(1)).findById(applicationId);
            verifyThatApplicationUpdated(updateRequest, 1);
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
            ApplicationEntity expectedEntity = applicationEntityFactory.createDefault(builder ->
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

        public final Stream<Arguments> invalidApplicationUpdateRequests() {
            return Stream.of(
                    Arguments.of(UUID.randomUUID(),
                            applicationUpdateRequestFactory.createDefault(builder -> builder
                                    .applicationContent(null)),
                            new ValidationException(List.of(
                                    "ApplicationUpdateRequest and its content cannot be null"
                            ))
                    ),
                    Arguments.of(UUID.randomUUID(),
                            applicationUpdateRequestFactory.createDefault(builder -> builder
                                    .applicationContent(new HashMap<>())),
                            new ValidationException(List.of(
                                    "Application content cannot be empty"
                            ))
                    )
            );
        }

        private void verifyThatApplicationUpdated(ApplicationUpdateRequest applicationUpdateRequest, int timesCalled) {
            ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
            verify(applicationRepository, times(timesCalled)).save(captor.capture());
            ApplicationEntity actualApplicationEntity = captor.getValue();

            assertThat(actualApplicationEntity.getStatus()).isEqualTo(applicationUpdateRequest.getStatus());
            assertThat(actualApplicationEntity.getApplicationContent())
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .isEqualTo(applicationUpdateRequest.getApplicationContent());
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

            CaseworkerEntity expectedCaseworker = caseworkerFactory.createDefault();

            ApplicationEntity existingApplicationEntity = applicationEntityFactory.createDefault(builder ->
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

            verifyThatApplicationEntitySaved(expectedApplicationEntity, 1);
            verifyThatDomainEventSaved(expectedDomainEvent, 1);
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
            CaseworkerEntity expectedCaseworker = caseworkerFactory.createDefault();
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
            ApplicationEntity existingApplicationEntity = applicationEntityFactory.createDefault(builder ->
                    builder.id(existingApplicationId).caseworker(null)
            );

            CaseworkerEntity expectedCaseworker = caseworkerFactory.createDefault();

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

            verifyThatApplicationEntitySaved(expectedApplicationEntity, 1);
            verifyThatDomainEventSaved(expectedDomainEvent, 1);
        }

        @Test
        void givenApplicationIsAlreadyAssignedToCaseworker_whenAssignCaseworker_thenNotUpdateApplication_andCreateDomainEvent() throws JsonProcessingException {

            UUID existingApplicationId = UUID.randomUUID();
            CaseworkerEntity existingCaseworker = caseworkerFactory.createDefault();
            ApplicationEntity existingApplicationEntity = applicationEntityFactory.createDefault(builder ->
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
            verifyThatDomainEventSaved(expectedDomainEvent, 1);
        }

        @Test
        void givenMissingApplications_whenAssignCaseworker_thenThrowResourceNotFoundException() {
            UUID existingApplicationId = UUID.randomUUID();
            ApplicationEntity existingApplicationEntity = applicationEntityFactory.createDefault(builder ->
                    builder.id(existingApplicationId).caseworker(null)
            );

            CaseworkerEntity expectedCaseworker = caseworkerFactory.createDefault();

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

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UnassignCaseworker {

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

            verifyThatApplicationEntitySaved(expectedApplicationEntity, 1);
            verifyThatDomainEventSaved(expectedDomainEvent, 1);
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

    @Nested
    class ReassignCaseworker {

        @Test
        void givenApplicationWithCaseworker_whenReassignCaseworker_thenSaveAndCreateDomainEvent() throws JsonProcessingException {

            // given
            UUID applicationId = UUID.randomUUID();

            CaseworkerEntity existingCaseworker = caseworkerFactory.createDefault(builder ->
                    builder.id(UUID.randomUUID())
                            .username("John Doe")
            );

            CaseworkerEntity expectedCaseworker = caseworkerFactory.createDefault(builder ->
                    builder.id(UUID.randomUUID())
                            .username("Jane Doe")
            );

            ApplicationEntity existingApplicationEntity = applicationEntityFactory.createDefault(builder ->
                    builder.id(applicationId).caseworker(existingCaseworker)
            );

            ApplicationEntity expectedApplicationEntity = existingApplicationEntity.toBuilder().caseworker(expectedCaseworker).build();

            EventHistory eventHistory = EventHistory.builder()
                    .eventDescription("Case reassigned.")
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

            verifyThatApplicationEntitySaved(expectedApplicationEntity, 1);
            verifyThatDomainEventSaved(expectedDomainEvent, 1);
        }
    }

    // <editor-fold desc="Shared asserts">

    private void assertIndividualCollectionsEqual(List<Individual> expectedList, Set<IndividualEntity> actualList) {

        assertThat(actualList).hasSameSizeAs(expectedList);

        for (Individual expected : expectedList) {
            boolean match = actualList.stream()
                    .anyMatch(actual -> {
                        try {
                            assertIndividualEqual(expected, actual);
                            return true;
                        } catch (AssertionError e) {
                            return false;
                        }
                    });
            assertThat(match)
                    .as("No matching IndividualEntity found for expected: " + expected)
                    .isTrue();
        }
    }

    private void assertIndividualEqual(Individual expected, IndividualEntity actual) {
        assertThat(actual.getFirstName()).isEqualTo(expected.getFirstName());
        assertThat(actual.getLastName()).isEqualTo(expected.getLastName());
        assertThat(actual.getDateOfBirth()).isEqualTo(expected.getDateOfBirth());
        assertThat(actual.getIndividualContent())
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .isEqualTo(expected.getDetails());
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

    // </editor-fold>
}