package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateEntityGenerator;

class GetCertificateGatewayMapperTest {

  private GetCertificateGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GetCertificateGatewayMapper();
  }

  @Test
  void givenFullyPopulatedEntity_whenToCertificateDomain_thenCertificateContentPreserved() {
    CertificateEntity certificateEntity =
        DataGenerator.createDefault(
            CertificateEntityGenerator.class, builder -> builder.applicationId(UUID.randomUUID()));

    CertificateDomain result = mapper.toCertificateDomain(certificateEntity);

    assertThat(result)
        .usingRecursiveComparison()
        .isEqualTo(new CertificateDomain(certificateEntity.getCertificateContent()));
    assertThat(result.certificateContent()).isEqualTo(certificateEntity.getCertificateContent());
  }

  @Test
  void givenEmptyCertificateContent_whenToCertificateDomain_thenEmptyMapReturned() {
    CertificateEntity certificateEntity =
        DataGenerator.createDefault(
            CertificateEntityGenerator.class, builder -> builder.certificateContent(Map.of()));

    CertificateDomain result = mapper.toCertificateDomain(certificateEntity);

    assertThat(result.certificateContent()).isEmpty();
  }
}
