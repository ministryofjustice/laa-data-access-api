package uk.gov.justice.laa.dstew.access.service.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.access.service.application.sharedAsserts.IndividualAssert.assertIndividualCollectionsEqual;

public class GetApplicationTest extends BaseServiceTest {

    @Autowired
    private ApplicationService serviceUnderTest;

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
        assertThat(actualApplication.getStatus()).isEqualTo(expectedApplication.getStatus());
        assertThat(actualApplication.getLaaReference()).isEqualTo(expectedApplication.getLaaReference());

    }
}
