package uk.gov.justice.laa.dstew.access.domain;

import lombok.Builder;

/** Domain record for provider details exposed by the get-application read model. */
@Builder(toBuilder = true)
public record ProviderDomain(String officeCode, String contactEmail) {}
