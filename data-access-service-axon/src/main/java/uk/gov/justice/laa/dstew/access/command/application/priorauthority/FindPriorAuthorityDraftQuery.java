package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.UUID;

/**
 * Asks for the open draft of a Prior Authority, answered from its events rather than a projection.
 * A submitted Prior Authority, or a draft whose content has been erased, has none.
 */
public record FindPriorAuthorityDraftQuery(UUID priorAuthorityId) {}
