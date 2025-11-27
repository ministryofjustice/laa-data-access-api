package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        @Test
        public void givenNewApplication_whenCreateApplication_thenReturnNewId() {

            // given
            String expectedReference = "REF7327";
            ApplicationCreateRequest request = ApplicationCreateRequest.builder()
                    .status(ApplicationStatus.IN_PROGRESS)
                    .applicationReference(expectedReference)
                    .applicationContent(new HashMap<>() {{
                        put("test", "content");
                    }})
                    .build();

            UUID expectedId = UUID.randomUUID();
            ApplicationEntity withExpectedId = ApplicationEntityFactory.create(builder ->
                builder.id(expectedId)
            );
            ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);
            when(repository.save(any())).thenReturn(withExpectedId);



            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .withRoles(TestConstants.Roles.WRITER)
                    .build();

            // when
            UUID actualId = sut.createApplication(request);

            // then
            assertEquals(expectedId, actualId);
            verify(repository, times(1)).save(any());
        }
    }

    @Nested
    class Update {

    }
}