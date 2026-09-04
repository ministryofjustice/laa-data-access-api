package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;

/** Queries whether a prior-authority current-state projection has reached PENDING. */
public record PriorAuthorityPendingByPriorAuthorityIdQuery(UUID priorAuthorityId) {}
