package uk.gov.justice.laa.dstew.access.query.individual;

import java.util.List;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;

/** Paginated individuals query result and optional application-level client details. */
public record FindIndividualsResult(
    List<ApplicationIndividual> individuals,
    int page,
    int pageSize,
    int totalRecords,
    ApplicationClientDetails clientDetails) {}
