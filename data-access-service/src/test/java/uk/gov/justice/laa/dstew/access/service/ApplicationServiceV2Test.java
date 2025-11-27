package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.doubles.ApplicationServiceDouble;
import uk.gov.justice.laa.dstew.access.utils.factory.ApplicationEntityFactory;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationEqual;

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

            // when
            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .withRoles(TestConstants.Roles.READER)
                    .build();

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

            // when
            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .withRoles(TestConstants.Roles.NO_ROLE)
                    .build();

            // then
            // will this work? the repository is what is throwing the exception...
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> sut.getApplication(applicationId))
            .withMessageContaining("Access Denied");

            verify(repository, times(0)).findById(applicationId);
        }
    }

    @Nested
    class GetAll {
    }

    @Nested
    class Create {

    }

    @Nested
    class Update {

    }
}