package uk.gov.justice.laa.dstew.access.command.application.draft;

/**
 * Result of a v2 create-draft write.
 *
 * @param projected {@code true} when the projection confirmed the draft is readable
 * @param draftVersion the optimistic-lock version of the draft after the write
 */
public record ApplicationDraftSaveResult(boolean projected, long draftVersion) {}
