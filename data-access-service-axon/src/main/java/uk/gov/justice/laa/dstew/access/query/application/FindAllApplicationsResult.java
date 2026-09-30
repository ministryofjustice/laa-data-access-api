package uk.gov.justice.laa.dstew.access.query.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;

/**
 * Result returned by {@link FindAllApplicationsQuery}, carrying the page of application read
 * models, batch-fetched group data keyed by lead application ID, batch-fetched prior authorities
 * keyed by application ID, and validated pagination metadata.
 */
public record FindAllApplicationsResult(
    List<ApplicationReadModel> applications,
    Map<UUID, LinkedApplicationGroupReadModel> groupsByLeadId,
    Map<UUID, List<PriorAuthorityReadModel>> priorAuthoritiesByApplicationId,
    long totalElements,
    int requestedPage,
    int requestedPageSize) {}
