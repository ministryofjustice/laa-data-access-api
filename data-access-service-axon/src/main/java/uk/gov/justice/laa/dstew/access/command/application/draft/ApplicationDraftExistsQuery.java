package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.util.UUID;

/**
 * Asks whether an Application has an open draft, answered from its events rather than a projection.
 * A submitted Application, or a draft whose content has been erased, has none.
 */
public record ApplicationDraftExistsQuery(UUID applicationId) {}
