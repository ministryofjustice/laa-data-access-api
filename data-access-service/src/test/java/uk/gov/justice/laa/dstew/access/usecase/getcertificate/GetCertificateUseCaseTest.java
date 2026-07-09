package uk.gov.justice.laa.dstew.access.usecase.getcertificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure.GetCertificateApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure.GetCertificateCertificateGateway;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.CertificateDomainGenerator;

@ExtendWith(MockitoExtension.class)
class GetCertificateUseCaseTest {

  @Mock private GetCertificateApplicationGateway applicationGateway;
  @Mock private GetCertificateCertificateGateway certificateGateway;

  private GetCertificateUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetCertificateUseCase(applicationGateway, certificateGateway);
  }

  @Test
  void givenExistingCertificate_whenExecuted_thenReturnsCertificateDomain() {
    UUID applicationId = UUID.randomUUID();
    CertificateDomain expected = DataGenerator.createDefault(CertificateDomainGenerator.class);

    when(applicationGateway.applicationExists(applicationId)).thenReturn(true);
    when(certificateGateway.findCertificateDomainByApplicationId(applicationId))
        .thenReturn(Optional.of(expected));

    CertificateDomain result = useCase.execute(applicationId);

    assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    assertThat(result.certificateContent()).isEqualTo(expected.certificateContent());
    verify(applicationGateway, times(1)).applicationExists(applicationId);
    verify(certificateGateway, times(1)).findCertificateDomainByApplicationId(applicationId);
  }

  @Test
  void givenApplicationNotFound_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();

    when(applicationGateway.applicationExists(applicationId)).thenReturn(false);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(applicationId))
        .withMessageContaining("No application found with id: " + applicationId);

    verify(applicationGateway, times(1)).applicationExists(applicationId);
    verify(certificateGateway, never()).findCertificateDomainByApplicationId(any(UUID.class));
  }

  @Test
  void givenCertificateNotFound_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();

    when(applicationGateway.applicationExists(applicationId)).thenReturn(true);
    when(certificateGateway.findCertificateDomainByApplicationId(applicationId))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(applicationId))
        .withMessageContaining("No certificate found for application id: " + applicationId);

    verify(applicationGateway, times(1)).applicationExists(applicationId);
    verify(certificateGateway, times(1)).findCertificateDomainByApplicationId(applicationId);
  }

  @Test
  void givenExecuteMethod_whenAnnotationsInspected_thenAllowApiCaseworkerIsPresent()
      throws Exception {
    var method = GetCertificateUseCase.class.getMethod("execute", UUID.class);
    assertThat(method.isAnnotationPresent(AllowApiCaseworker.class)).isTrue();
  }
}
