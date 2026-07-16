package uk.gov.justice.laa.dstew.access.query.submission;

import java.util.List;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.SynchronousApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.SynchronousApplicationProceeding;

/**
 * The raw submitted payload stored as the {@code submissions.data} JSON blob. Kept out of the
 * queryable read model so the read model holds only true queryable metadata; the payload (including
 * bulky/PII content) lives here and is rebuilt on demand to serve the full GET response.
 */
public record SubmissionData(
    ApplicationContent applicationContent,
    List<SynchronousApplicationIndividual> individuals,
    List<SynchronousApplicationProceeding> proceedings) {}
