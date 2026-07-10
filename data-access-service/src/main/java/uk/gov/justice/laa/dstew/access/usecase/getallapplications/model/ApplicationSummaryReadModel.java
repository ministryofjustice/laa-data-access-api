package uk.gov.justice.laa.dstew.access.usecase.getallapplications.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Read model representing a summary of a single legal aid application. */
@Builder(toBuilder = true)
public record ApplicationSummaryReadModel(
    UUID id,
    Instant submittedAt,
    Boolean isAutoGranted,
    String categoryOfLaw,
    String matterType,
    Boolean usedDelegatedFunctions,
    String laaReference,
    String officeCode,
    String status,
    UUID caseworkerId,
    String clientFirstName,
    String clientLastName,
    LocalDate clientDateOfBirth,
    String applicationType,
    Instant modifiedAt,
    Boolean isLead,
    List<LinkedApplicationSummaryReadModel> linkedApplications) {}
