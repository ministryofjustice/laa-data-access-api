package uk.gov.justice.laa.dstew.access.query.application;

import java.util.List;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;

/** Related read models required to build an Application response. */
public record ApplicationAssociationsResult(
    LinkedApplicationGroupReadModel linkedGroup, List<PriorAuthorityReadModel> priorAuthorities) {}
