package uk.gov.justice.laa.dstew.access.usecase.getallapplications;

import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * Input record carrying all query parameters for the getAllApplications use case. All API-model
 * enum params are stored as nullable Strings (enum names) — no API model imports.
 */
@Builder(toBuilder = true)
public record GetAllApplicationsQuery(
    String status,
    String laaReference,
    String clientFirstName,
    String clientLastName,
    LocalDate clientDateOfBirth,
    UUID userId,
    Boolean isAutoGranted,
    String matterType,
    String sortBy,
    String orderBy,
    Integer page,
    Integer pageSize) {}
