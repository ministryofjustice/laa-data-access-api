package uk.gov.justice.laa.dstew.access.domain;

import java.util.Map;
import lombok.Builder;

/** Domain record representing a certificate. Pure Java — no JPA, no Spring imports. */
@Builder(toBuilder = true)
public record CertificateDomain(Map<String, Object> certificateContent) {}
