package uk.gov.justice.laa.dstew.access.service.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.CertificateService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateEntityGenerator;

public class GetCertificateTest extends BaseServiceTest {

  @Autowired private CertificateService serviceUnderTest;

  @Test
  public void givenExistingCertificate_whenGetCertificate_thenReturnCertificateContent() {
    // given
    ApplicationEntity applicationEntity =
        DataGenerator.createDefault(ApplicationEntityGenerator.class);
    UUID applicationId = applicationEntity.getId();

    CertificateEntity certificateEntity =
        DataGenerator.createDefault(
            CertificateEntityGenerator.class, builder -> builder.applicationId(applicationId));

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));
    when(certificateRepository.findByApplicationId(applicationId))
        .thenReturn(Optional.of(certificateEntity));

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    Map<String, Object> result = serviceUnderTest.getCertificate(applicationId);

    // then
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(certificateEntity.getCertificateContent());
    verify(applicationRepository, times(1)).findById(applicationId);
    verify(certificateRepository, times(1)).findByApplicationId(applicationId);
  }

  @Test
  public void givenApplicationNotExists_whenGetCertificate_thenThrowResourceNotFoundException() {
    // given
    UUID applicationId = UUID.randomUUID();

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    // then
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> serviceUnderTest.getCertificate(applicationId))
        .withMessageContaining("No application found with id: " + applicationId);

    verify(applicationRepository, times(1)).findById(applicationId);
    verify(certificateRepository, never()).findByApplicationId(any(UUID.class));
  }

  @Test
  public void givenNoCertificateForApplication_whenGetCertificate_thenThrowNotFoundException() {
    // given
    ApplicationEntity applicationEntity =
        DataGenerator.createDefault(ApplicationEntityGenerator.class);
    UUID applicationId = applicationEntity.getId();

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));
    when(certificateRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    // then
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> serviceUnderTest.getCertificate(applicationId))
        .withMessageContaining("No certificate found for application id: " + applicationId);
  }

  @Test
  public void givenNoRole_whenGetCertificate_thenThrowAuthorizationDeniedException() {
    // given
    UUID applicationId = UUID.randomUUID();

    setSecurityContext(TestConstants.Roles.NO_ROLE);

    // when
    // then
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.getCertificate(applicationId))
        .withMessageContaining("Access Denied");

    verify(applicationRepository, times(0)).findById(any(UUID.class));
    verify(certificateRepository, never()).findByApplicationId(any(UUID.class));
  }
}
