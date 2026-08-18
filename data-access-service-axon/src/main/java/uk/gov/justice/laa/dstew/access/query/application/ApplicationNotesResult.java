package uk.gov.justice.laa.dstew.access.query.application;

import java.util.List;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationNote;

/** Wraps the notes list returned by {@link FindNotesForApplicationQuery}. */
public record ApplicationNotesResult(List<ApplicationNote> notes) {}
