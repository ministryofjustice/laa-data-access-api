package uk.gov.justice.laa.dstew.access.domain;

import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/** Domain record representing a certificate. */
@Builder(toBuilder = true)
public record CertificateDomain(
    UUID id,
    UUID applicationId,
    Map<String, Object> certificateContent,
    String createdBy,
    String updatedBy) {}
