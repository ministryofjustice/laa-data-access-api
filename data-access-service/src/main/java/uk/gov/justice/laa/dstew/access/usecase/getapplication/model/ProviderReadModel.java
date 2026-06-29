package uk.gov.justice.laa.dstew.access.usecase.getapplication.model;

import lombok.Builder;

/** Read-model record for provider details on the get-application response. */
@Builder(toBuilder = true)
public record ProviderReadModel(String officeCode, String contactEmail) {}
