package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;

class GetCertificateGatewayMapperTest {

  private GetCertificateGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GetCertificateGatewayMapper();
  }

  @Test
  void givenFullyPopulatedEntity_whenToCertificateDomain_thenCertificateContentPreservedExactly() {
    // given
    UUID id = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content =
        Map.of(
            "certificateNumber", "TESTCERT001",
            "issueDate", "2026-03-03",
            "validUntil", "2027-03-03");

    CertificateEntity entity = new CertificateEntity();
    entity.setId(id);
    entity.setApplicationId(applicationId);
    entity.setCertificateContent(content);
    entity.setCreatedBy("test-user");
    entity.setUpdatedBy("test-user");

    // when
    CertificateDomain domain = mapper.toCertificateDomain(entity);

    // then
    assertThat(domain.id()).isEqualTo(id);
    assertThat(domain.applicationId()).isEqualTo(applicationId);
    assertThat(domain.certificateContent()).isEqualTo(content);
    assertThat(domain.createdBy()).isEqualTo("test-user");
    assertThat(domain.updatedBy()).isEqualTo("test-user");
  }

  @Test
  void givenEntityWithEmptyCertificateContent_whenToCertificateDomain_thenEmptyMapReturned() {
    // given
    CertificateEntity entity = new CertificateEntity();
    entity.setId(UUID.randomUUID());
    entity.setApplicationId(UUID.randomUUID());
    entity.setCertificateContent(Map.of());
    entity.setCreatedBy("test-user");
    entity.setUpdatedBy("test-user");

    // when
    CertificateDomain domain = mapper.toCertificateDomain(entity);

    // then
    assertThat(domain.certificateContent()).isEmpty();
  }
}
