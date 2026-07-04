package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.CertificateDomainGenerator;

class MakeDecisionGatewayMapperTest {

  private MakeDecisionGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new MakeDecisionGatewayMapper();
  }

  @Test
  void givenFullyPopulatedEntity_whenToCertificateDomain_thenAllFieldsMapped() {
    UUID id = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    CertificateEntity entity = new CertificateEntity();
    entity.setId(id);
    entity.setApplicationId(applicationId);
    entity.setCertificateContent(Map.of("key", "value"));
    entity.setCreatedBy("creator");
    entity.setUpdatedBy("updater");

    CertificateDomain domain = mapper.toCertificateDomain(entity);

    assertThat(domain.id()).isEqualTo(id);
    assertThat(domain.applicationId()).isEqualTo(applicationId);
    assertThat(domain.certificateContent()).isEqualTo(Map.of("key", "value"));
    assertThat(domain.createdBy()).isEqualTo("creator");
    assertThat(domain.updatedBy()).isEqualTo("updater");
  }

  @Test
  void givenCertificateDomain_whenToCertificateEntity_thenNonHibernateFieldsSetAndIdIsNull() {
    CertificateDomain domain =
        DataGenerator.createDefault(CertificateDomainGenerator.class, b -> b.id(null));

    CertificateEntity entity = mapper.toCertificateEntity(domain);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getApplicationId()).isEqualTo(domain.applicationId());
    assertThat(entity.getCertificateContent()).isEqualTo(domain.certificateContent());
    assertThat(entity.getCreatedBy()).isEqualTo(domain.createdBy());
    assertThat(entity.getUpdatedBy()).isEqualTo(domain.updatedBy());
  }

  @Test
  void givenCertificateDomainWithNullCreatedBy_whenToCertificateEntity_thenCreatedByIsNull() {
    CertificateDomain domain =
        DataGenerator.createDefault(
            CertificateDomainGenerator.class, b -> b.id(null).createdBy(null).updatedBy(null));

    CertificateEntity entity = mapper.toCertificateEntity(domain);

    assertThat(entity.getCreatedBy()).isNull();
    assertThat(entity.getUpdatedBy()).isNull();
  }
}
