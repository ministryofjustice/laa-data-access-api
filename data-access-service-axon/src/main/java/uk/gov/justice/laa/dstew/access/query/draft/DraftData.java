package uk.gov.justice.laa.dstew.access.query.draft;

import java.util.List;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationProceeding;

/**
 * The draft payload stored as the {@code drafts.data} JSON blob. Same shape as the submission
 * payload, but held in the mutable drafts store and edited in place before submission.
 */
public record DraftData(
    ApplicationContent applicationContent,
    List<ApplicationIndividual> individuals,
    List<ApplicationProceeding> proceedings) {}
