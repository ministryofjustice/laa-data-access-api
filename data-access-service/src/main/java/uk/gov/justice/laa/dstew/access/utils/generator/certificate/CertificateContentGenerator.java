package uk.gov.justice.laa.dstew.access.utils.generator.certificate;

import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.testDto.certificate.CertificateContent;

public class CertificateContentGenerator
    extends BaseGenerator<CertificateContent, CertificateContent.CertificateContentBuilder> {

  public CertificateContentGenerator() {
    super(CertificateContent::toBuilder, CertificateContent.CertificateContentBuilder::build);
  }

  @Override
  public CertificateContent createDefault() {
    return CertificateContent.builder()
        .certificateNumber("TESTCERT001")
        .issueDate("2026-03-03")
        .validUntil("2027-03-03")
        .build();
  }
}
