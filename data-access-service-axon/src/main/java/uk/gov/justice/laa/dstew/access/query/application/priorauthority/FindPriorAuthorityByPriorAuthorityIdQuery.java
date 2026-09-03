package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;

/** Query returning the current-state projection for a single prior-authority submission. */
public record FindPriorAuthorityByPriorAuthorityIdQuery(UUID priorAuthorityId) {}
