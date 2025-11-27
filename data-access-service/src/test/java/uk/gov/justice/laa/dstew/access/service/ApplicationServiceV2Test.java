package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import static org.mockito.Mockito.*;

public class ApplicationServiceV2Test extends BaseServiceTest {

    @Nested
    class Get {
        @Test
        public void givenApplicationEntity_whenGetApplication_thenReturnMappedApplication() {
            // given
            ApplicationEntity entity = ApplicationEntityFactory.create();

            ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);
            when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

            ApplicationService sut = new ApplicationServiceDouble()
                    .withRepository(repository)
                    .withRoles(TestConstants.Roles.READER)
                    .build();

            // when
            Application result = sut.getApplication(entity.getId());

            // then
            assertThat(result).isNotNull();
            // other asserts to ensure mapper maps ApplicationEntity to Application
            // here
        }

        @Test
        public void givenNoApplication_whenGetApplication_thenThrowApplicationNotFoundException() {

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
            ApplicationEntity expectedToCreate = ApplicationEntityFactory.create(builder -> {
                builder.id(expectedId);
                builder.applicationReference(expectedReference);
            });

            // when


            // then

        }
    }

    @Nested
    class Update {

    }
}