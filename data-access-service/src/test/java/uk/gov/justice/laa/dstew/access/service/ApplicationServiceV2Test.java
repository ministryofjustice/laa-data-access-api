package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
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
import uk.gov.justice.laa.dstew.access.utils.testData.ApplicationTestData;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationEqual;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationsEqualAndIgnore;

public class ApplicationServiceV2Test extends BaseServiceTest {

    @Nested
    class Get {

        @Test
        public void givenApplicationEntityAndRoleReader_whenGetApplication_thenReturnMappedApplication() {
            // given
            ApplicationEntity expected = ApplicationEntityFactory.create();

            ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);
            when(repository.findById(expected.getId())).thenReturn(Optional.of(expected));

            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .withRoles(TestConstants.Roles.READER)
                    .build();

            // when
            Application actual = sut.getApplication(expected.getId());

            // then
            assertThat(actual).isNotNull();
            assertApplicationEqual(expected, actual);
            verify(repository).findById(expected.getId());
        }

        @Test
        public void givenNoApplicationAndRoleReader_whenGetApplication_thenThrowApplicationNotFoundException() {

            // given
            UUID applicationId = UUID.randomUUID();
            ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);
            when(repository.findById(applicationId)).thenThrow(ApplicationNotFoundException.class);

            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .withRoles(TestConstants.Roles.READER)
                    .build();

            // when
            // then
            // will this work? the repository is what is throwing the exception...
            assertThatExceptionOfType(ApplicationNotFoundException.class)
                    .isThrownBy(() -> sut.getApplication(applicationId));
            //.withMessageContaining("No application found with id: " + applicationId);
            verify(repository, times(1)).findById(applicationId);
        }

        @Test
        public void givenApplicationAndNotRoleReader_whenGetApplication_thenThrowUnauthorizedException() {

            // given
            UUID applicationId = UUID.randomUUID();
            ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);

            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .withRoles(TestConstants.Roles.NO_ROLE)
                    .build();

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> sut.getApplication(applicationId))
            .withMessageContaining("Access Denied");

            verify(repository, times(0)).findById(applicationId);
        }

        @Test
        public void givenApplicationAndNoRole_whenGetApplication_thenThrowUnauthorizedException() {

            // given
            ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);

            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .build();

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> sut.getApplication(UUID.randomUUID()))
                    .withMessageContaining("Access Denied");

            verify(repository, times(0)).findById(any(UUID.class));
        }
    }

    @Nested
    class GetAll {
    }

    @Nested
    class Create {

        @Test
        public void givenNewApplication_whenCreateApplication_thenReturnNewId() {

            // given
            UUID expectedId = UUID.randomUUID();
            ApplicationEntity withExpectedId = ApplicationEntityFactory.create(builder ->
                builder.id(expectedId)
            );
            ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);
            when(repository.save(any())).thenReturn(withExpectedId);

            ApplicationEntity expectedInRepositorySave = ApplicationEntityFactory.create(builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
                builder.applicationReference(ApplicationTestData.Create.TO_CREATE_REFERENCE);
                builder.schemaVersion(1);
            });

            ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);

            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .withRoles(TestConstants.Roles.WRITER)
                    .build();

            // when
            UUID actualId = sut.createApplication(ApplicationTestData.Create.TO_CREATE);

            // then
            assertEquals(expectedId, actualId);
            verify(repository).save(captor.capture());

            ApplicationEntity actualInRepositorySave = captor.getValue();
            assertApplicationsEqualAndIgnore(expectedInRepositorySave, actualInRepositorySave, "id", "createdAt", "modifiedAt");
            assertThat(actualInRepositorySave.getId()).isNotNull();

            verify(repository, times(1)).save(any());
        }

        @Test
        public void givenNewApplicationAndNotRoleReader_whenCreateApplication_thenThrowUnauthorizedException() {
            // given
            ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);

            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .withRoles(TestConstants.Roles.NO_ROLE)
                    .build();

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> sut.createApplication(ApplicationTestData.Create.TO_CREATE))
                    .withMessageContaining("Access Denied");

            verify(repository, times(0)).findById(any(UUID.class));
        }

        @Test
        public void givenNewApplicationAndNoRole_whenCreateApplication_thenThrowUnauthorizedException() {
            // given
            ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);

            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .build();

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> sut.createApplication(ApplicationTestData.Create.TO_CREATE))
                    .withMessageContaining("Access Denied");

            verify(repository, times(0)).findById(any(UUID.class));
        }

        @ParameterizedTest
        @MethodSource("invalidRequests")
        public void GivenInvalidApplicationAndRoleWriter_whenCreateApplication_thenValidationExceptionWithCorrectMessage(
                ApplicationCreateRequest applicationCreateRequest,
                ValidationException validationException
        ) {
            // given
            ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);

            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .withRoles(TestConstants.Roles.WRITER)
                    .build();

            // when
            // then
            Throwable thrown = catchThrowable(() -> sut.createApplication(applicationCreateRequest));
            assertThat(thrown)
                    .isInstanceOf(ValidationException.class)
                    .usingRecursiveComparison()
                    .isEqualTo(validationException);

            verify(repository, times(0)).findById(any(UUID.class));
        }

        public static Stream<Arguments> invalidRequests() {
            return ApplicationTestData.Create.INVALID_REQUESTS;
        }
    }

    @Nested
    class Update {

    }
}