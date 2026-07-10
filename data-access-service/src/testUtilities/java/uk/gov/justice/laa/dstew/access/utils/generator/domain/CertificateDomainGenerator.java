package uk.gov.justice.laa.dstew.access.utils.generator.domain;

import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link CertificateDomain} test instances. */
public class CertificateDomainGenerator
    extends BaseGenerator<CertificateDomain, CertificateDomain.CertificateDomainBuilder> {

  /** Constructs the generator. */
  public CertificateDomainGenerator() {
    super(CertificateDomain::toBuilder, CertificateDomain.CertificateDomainBuilder::build);
  }

  @Override
  public CertificateDomain createDefault() {
    return CertificateDomain.builder()
        .id(UUID.randomUUID())
        .applicationId(UUID.randomUUID())
        .certificateContent(
            Map.of(
                "certificateNumber", "TESTCERT001",
                "issueDate", "2026-03-03",
                "validUntil", "2027-03-03"))
        .createdBy("test-user")
        .updatedBy("test-user")
        .build();
  }
}
