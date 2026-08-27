package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;

/** Query to find all prior-authority projections for a given application id. */
public record FindPriorAuthoritiesByApplicationIdQuery(UUID applicationId) {}
