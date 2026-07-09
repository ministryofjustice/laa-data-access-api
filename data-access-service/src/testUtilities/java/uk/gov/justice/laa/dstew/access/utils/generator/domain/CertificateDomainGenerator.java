package uk.gov.justice.laa.dstew.access.utils.generator.domain;

import java.util.Map;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link CertificateDomain} test data. */
public class CertificateDomainGenerator
    extends BaseGenerator<CertificateDomain, CertificateDomain.CertificateDomainBuilder> {

  /** Creates the generator. */
  public CertificateDomainGenerator() {
    super(CertificateDomain::toBuilder, CertificateDomain.CertificateDomainBuilder::build);
  }

  @Override
  public CertificateDomain createDefault() {
    return CertificateDomain.builder()
        .certificateContent(
            Map.of(
                "certificateNumber", "TESTCERT001",
                "issueDate", "2026-03-03",
                "validUntil", "2027-03-03"))
        .build();
  }
}
