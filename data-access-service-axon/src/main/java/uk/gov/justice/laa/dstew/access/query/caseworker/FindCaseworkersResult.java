package uk.gov.justice.laa.dstew.access.query.caseworker;

import java.util.List;
import uk.gov.justice.laa.dstew.access.command.caseworker.Caseworker;

/** Result of a FindCaseworkersQuery containing all known caseworkers. */
public record FindCaseworkersResult(List<Caseworker> caseworkers) {}
