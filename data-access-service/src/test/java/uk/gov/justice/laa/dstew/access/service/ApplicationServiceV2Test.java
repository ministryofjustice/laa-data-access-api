package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.doubles.ApplicationServiceDouble;
import uk.gov.justice.laa.dstew.access.utils.factory.ApplicationEntityFactory;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationEqual;

public class ApplicationServiceV2Test extends BaseServiceTest {

    private ApplicationRepository applicationRepository;

    @BeforeEach
    void setUp() {
        applicationRepository = Mockito.mock(ApplicationRepository.class);
    }

    @Nested
    class GetApplication {

        @Test
        public void givenApplicationEntityAndRoleReader_whenGetApplication_thenReturnMappedApplication() {
            // given
            ApplicationEntity expectedApplication = ApplicationEntityFactory.create();

            when(applicationRepository.findById(expectedApplication.getId())).thenReturn(Optional.of(expectedApplication));

            ApplicationService sut = new ApplicationServiceDouble()
                    .withApplicationRepository(applicationRepository)
                    .withRoles(TestConstants.Roles.READER)
                    .build();

            // when
            Application actualApplication = sut.getApplication(expectedApplication.getId());

            // then
            assertApplicationEqual(expectedApplication, actualApplication);
            verify(applicationRepository, times(1)).findById(expectedApplication.getId());
        }

        @Test
        public void givenNoApplicationAndRoleReader_whenGetApplication_thenThrowApplicationNotFoundException() {

            // given
            UUID applicationId = UUID.randomUUID();
            when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

            ApplicationService sut = new ApplicationServiceDouble()
                    .withApplicationRepository(applicationRepository)
                    .withRoles(TestConstants.Roles.READER)
                    .build();

            // when
            // then
            assertThatExceptionOfType(ApplicationNotFoundException.class)
                    .isThrownBy(() -> sut.getApplication(applicationId))
                    .withMessageContaining("No application found with id: " + applicationId);
            verify(applicationRepository, times(1)).findById(applicationId);
        }

        @Test
        public void givenApplicationAndNotRoleReader_whenGetApplication_thenThrowUnauthorizedException() {

            // given
            UUID applicationId = UUID.randomUUID();

            ApplicationService sut = new ApplicationServiceDouble()
                    .withApplicationRepository(applicationRepository)
                    .withRoles(TestConstants.Roles.NO_ROLE)
                    .build();

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> sut.getApplication(applicationId))
            .withMessageContaining("Access Denied");

            verify(applicationRepository, times(0)).findById(applicationId);
        }

        @Test
        public void givenApplicationAndNoRole_whenGetApplication_thenThrowUnauthorizedException() {

            // given
            ApplicationService sut = new ApplicationServiceDouble()
                    .withApplicationRepository(applicationRepository)
                    .build();

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> sut.getApplication(UUID.randomUUID()))
                    .withMessageContaining("Access Denied");

            verify(applicationRepository, times(0)).findById(any(UUID.class));
        }
    }

    @Nested
    class GetAllApplication {
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

            ApplicationService sut = new ApplicationServiceDouble()
                    .withApplicationRepository(applicationRepository)
                    .withRoles(TestConstants.Roles.WRITER)
                    .build();

            // when
            UUID actualId = sut.createApplication(APPLICATION_TO_CREATE);

            // then
            assertEquals(expectedId, actualId);

            verify(applicationRepository, times(1)).save(any());
        }

        @Test
        public void givenNewApplicationAndNotRoleReader_whenCreateApplication_thenThrowUnauthorizedException() {
            // given
            ApplicationService sut = new ApplicationServiceDouble()
                    .withApplicationRepository(applicationRepository)
                    .withRoles(TestConstants.Roles.NO_ROLE)
                    .build();

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> sut.createApplication(APPLICATION_TO_CREATE))
                    .withMessageContaining("Access Denied");

            verify(applicationRepository, times(0)).findById(any(UUID.class));
        }

        @Test
        public void givenNewApplicationAndNoRole_whenCreateApplication_thenThrowUnauthorizedException() {
            // given
            ApplicationService sut = new ApplicationServiceDouble()
                    .withApplicationRepository(applicationRepository)
                    .build();

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> sut.createApplication(APPLICATION_TO_CREATE))
                    .withMessageContaining("Access Denied");

            verify(applicationRepository, times(0)).findById(any(UUID.class));
        }

        @ParameterizedTest
        @MethodSource("invalidApplicationRequests")
        public void GivenInvalidApplicationAndRoleWriter_whenCreateApplication_thenValidationExceptionWithCorrectMessage(
                ApplicationCreateRequest applicationCreateRequest,
                ValidationException validationException
        ) {
            // given
            ApplicationService sut = new ApplicationServiceDouble()
                    .withApplicationRepository(applicationRepository)
                    .withRoles(TestConstants.Roles.WRITER)
                    .build();

            // when
            // then
            Throwable thrown = catchThrowable(() -> sut.createApplication(applicationCreateRequest));
            assertThat(thrown)
                    .isInstanceOf(ValidationException.class)
                    .usingRecursiveComparison()
                    .isEqualTo(validationException);

            verify(applicationRepository, times(0)).findById(any(UUID.class));
        }

        private static final String APPLICATION_TO_CREATE_REFERENCE = "REF7327";
        private static final ApplicationCreateRequest APPLICATION_TO_CREATE = ApplicationCreateRequest.builder()
                .status(ApplicationStatus.IN_PROGRESS)
                .applicationReference(APPLICATION_TO_CREATE_REFERENCE)
                .applicationContent(new HashMap<>() {{
                    put("test", "content");
                }})
                .build();

        public static final Stream<Arguments> invalidApplicationRequests() {
            return Stream.of(
                    Arguments.of(ApplicationCreateRequest.builder()
                                    .status(ApplicationStatus.IN_PROGRESS)
                                    .applicationReference(APPLICATION_TO_CREATE_REFERENCE)
                                    .applicationContent(null)
                                    .build(),
                            new ValidationException(List.of(
                                    "ApplicationCreateRequest and its content cannot be null"
                            ))
                    ),
                    Arguments.of(ApplicationCreateRequest.builder()
                                    .applicationReference(APPLICATION_TO_CREATE_REFERENCE)
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
                                    .applicationReference(APPLICATION_TO_CREATE_REFERENCE)
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
    class Update {

    }
}