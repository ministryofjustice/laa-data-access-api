package uk.gov.justice.laa.dstew.access.query.individual;

import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;

/** Paginated individuals query result with client details from application data. */
public record FindIndividualsResult(
    ApplicationClient client,
    int page,
    int pageSize,
    int totalRecords,
    ApplicationClientDetails clientDetails) {}
