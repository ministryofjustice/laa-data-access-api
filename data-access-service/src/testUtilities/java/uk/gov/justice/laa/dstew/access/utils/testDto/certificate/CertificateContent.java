package uk.gov.justice.laa.dstew.access.utils.testDto.certificate;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder(toBuilder = true)
@Getter
@Setter
public class CertificateContent {
  public String certificateNumber;
  public String issueDate;
  public String validUntil;
}
